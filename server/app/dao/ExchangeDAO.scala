package dao

import java.sql.Date
import java.util.Calendar
import javax.inject.Inject

import models._
import play.api.db.slick.DatabaseConfigProvider
import play.db.NamedDatabase
import slick.backend.DatabaseConfig
import slick.driver.JdbcProfile
import slick.driver.SQLiteDriver.api._
import slick.lifted.{ForeignKeyQuery, MappedProjection, ProvenShape}
import utils.Const

import scala.concurrent.{ExecutionContext, Future}

class ExchangeDAO @Inject()(@NamedDatabase(Const.DbName) dbConfigProvider: DatabaseConfigProvider, userDAO: UserDAO)
                           (implicit executionContext: ExecutionContext) {
  private val dbConfig: DatabaseConfig[JdbcProfile] = dbConfigProvider.get[JdbcProfile]
  // initialisation of foreign key in SQLite
  dbConfig.db.run(DBIO.seq(sqlu"PRAGMA foreign_keys = ON;")).map { _ => () }

  private val exchanges: TableQuery[ExchangeTable] = TableQuery[ExchangeTable]

  def insert(userEmail: String, exchange: ExchangePOSTDTO): Future[Future[Unit]] = {
    // we get the id of the connected user
    userDAO.getId(userEmail).map { userId =>
      var dateToInsert: Date = null

      // test if a date is given or not (None value or not)
      if (exchange.date.isEmpty) {
        // we get the actual date if we don't give one
        dateToInsert = Date.valueOf(Const.format.format(Calendar.getInstance().getTime))
      }
      else {
        dateToInsert = Date.valueOf(exchange.date.get.year + "-" + exchange.date.get.month +
          "-" + exchange.date.get.day)
      }

      val exchangeToInsert = Exchange(exchange.name, dateToInsert, exchange.`type`, exchange.amount, userId)

      dbConfig.db.run(exchanges += exchangeToInsert).map { _ => () }
    }
  }

  def findAll(userEmail: String, dates: FromToDatesDTO): Future[Seq[ExchangeAllGETDTO]] = {
    dbConfig.db.run(exchanges.join(userDAO.users).on(_.userId === _.id).filter(_._2.email === userEmail)
      .map(_._1.exchangeInfo).result).map { e =>
      // if from and to dates are presents (JSON), we keep only the corresponding exchanges
      val exchangesToSendToKeep = e.filter{ t =>
        if (dates.from.isDefined) {
          val dateFrom = Date.valueOf(dates.from.get.year + "-" + dates.from.get.month + "-" + dates.from.get.day)

          t.date.equals(dateFrom) || t.date.after(dateFrom)
        }
        else {
          true
        }
      }.filter { t =>
        if (dates.to.isDefined) {
          val dateTo = Date.valueOf(dates.to.get.year + "-" + dates.to.get.month + "-" + dates.to.get.day)

          t.date.equals(dateTo) || t.date.before(dateTo)
        }
        else {
          true
        }
      }

      val exchangesToSend = exchangesToSendToKeep.map { t =>
        val dateToSend = Option(DateDTO(t.date.toString.substring(8, 10).toInt, t.date.toString.substring(5, 7).toInt,
          t.date.toString.substring(0, 4).toInt))
        val exchangeToSend = ExchangeAllGETDTO(t.id, t.name, dateToSend, t.`type`, t.amount)

        exchangeToSend
      }

      exchangesToSend
    }
  }

  def find(userEmail: String, id: Int): Future[ExchangeGETDTO] = {
    // with a join and the email of the connected user, we first verify that the asked exchange (id) belongs to this user
    // or exists
    dbConfig.db.run(exchanges.join(userDAO.users).on(_.userId === _.id).filter(_._2.email === userEmail)
      .filter(_._1.id === id).map(_._1.exchangeInfo).result.head).map { e =>
      val dateToSend = Option(DateDTO(e.date.toString.substring(8, 10).toInt, e.date.toString.substring(5, 7).toInt,
        e.date.toString.substring(0, 4).toInt))
      val exchangeToSend = ExchangeGETDTO(e.name, dateToSend, e.`type`, e.amount)

      exchangeToSend
    }
  }

  def update(userEmail: String, id: Int, exchange: ExchangePATCHDTO): Future[Any] = {
    // we first verify that the asked exchange (id) to update belongs to this user or exists
    dbConfig.db.run(exchanges.join(userDAO.users).on(_.userId === _.id).filter(_._2.email === userEmail)
      .filter(_._1.id === id).result.head).map { _ =>
      // we update only the present fields
      // not the value None
      if (exchange.name.isDefined) {
        dbConfig.db.run(exchanges.filter(_.id === id).map(_.name).update(exchange.name.get)).map { _ => () }
      }

      if (exchange.date.isDefined) {
        val dateToInsert = Date.valueOf(exchange.date.get.year + "-" + exchange.date.get.month +
          "-" + exchange.date.get.day)

        dbConfig.db.run(exchanges.filter(_.id === id).map(_.date).update(dateToInsert)).map { _ => () }
      }

      if (exchange.`type`.isDefined) {
        dbConfig.db.run(exchanges.filter(_.id === id).map(_.`type`).update(exchange.`type`.get)).map { _ => () }
      }

      if (exchange.amount.isDefined) {
        dbConfig.db.run(exchanges.filter(_.id === id).map(_.amount).update(exchange.amount.get)).map { _ => () }
      }
    }
  }

  def delete(userEmail: String, id: Int): Future[Unit] = {
    // we first verify that the asked exchange (id) to delete belongs to this user or exists
    dbConfig.db.run(exchanges.join(userDAO.users).on(_.userId === _.id).filter(_._2.email === userEmail)
      .filter(_._1.id === id).result.head).map { _ =>
      dbConfig.db.run(exchanges.filter(_.id === id).delete).map { _ => () }
    }
  }

  private class ExchangeTable(tag: Tag) extends Table[Exchange](tag, "exchange") {
    def id: Rep[Int] = column[Int]("id", O.PrimaryKey, O.AutoInc)
    def userId: Rep[Int] = column[Int]("userId")
    def name: Rep[String] = column[String]("name")
    def date: Rep[Date] = column[Date]("date")
    def `type`: Rep[String] = column[String]("type")
    def amount: Rep[Double] = column[Double]("amount")

    // A reified foreign key relation to an user that can be navigated to create a join
    // n to one relationship
    def user: ForeignKeyQuery[userDAO.UserTable, User] = {
      // when an user is deleted, his exchanges are also deleted (same with update)
      foreignKey("user_FK", userId, userDAO.users)(_.id, onDelete = ForeignKeyAction.Cascade,
        onUpdate = ForeignKeyAction.Cascade)
    }

    def * : ProvenShape[Exchange] = {
      (name, date, `type`, amount, userId) <> ((Exchange.apply _).tupled, Exchange.unapply)
    }

    def exchangeInfo: MappedProjection[ExchangeGET, (Int, String, Date, String, Double)] = {
      (id, name, date, `type`, amount) <> (ExchangeGET.tupled, ExchangeGET.unapply)
    }
  }
}
