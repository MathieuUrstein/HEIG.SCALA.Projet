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

  def findAll(userEmail: String): Future[Seq[ExchangeGET]] = {
    dbConfig.db.run(exchanges.join(userDAO.users).on(_.userId === _.id).filter(_._2.email === userEmail)
      .map(_._1.exchangeInfo).result)
  }

  def find(userEmail: String, id: Int): Future[ExchangeGET] = {
    // with a join and the email of the connected user, we first verify that the asked exchange (id) belongs to this user
    // or exists
    dbConfig.db.run(exchanges.join(userDAO.users).on(_.userId === _.id).filter(_._2.email === userEmail)
      .filter(_._1.id === id).map(_._1.exchangeInfo).result.head)
  }

  def update(userEmail: String, id: Int, exchange: ExchangePATCHDTO): Future[Unit] = {
    // we first verify that the asked exchange (id) to update belongs to this user or exists
    dbConfig.db.run(exchanges.join(userDAO.users).on(_.userId === _.id).filter(_._2.email === userEmail)
      .filter(_._1.id === id).map(_._1.exchangeInfo).result.head).map { _ =>
      // default future with success and do nothing
      var futureToReturn = Future.successful(())

      // we update only the present fields
      // not the value None
      if (exchange.name.isDefined) {
        futureToReturn = dbConfig.db.run(exchanges.filter(_.id === id).map(_.name).update(exchange.name.get))
          .map { _ => () }
      }

      if (exchange.date.isDefined) {
        val dateToInsert = Date.valueOf(exchange.date.get.year + "-" + exchange.date.get.month +
          "-" + exchange.date.get.day)

        futureToReturn = dbConfig.db.run(exchanges.filter(_.id === id).map(_.date).update(dateToInsert))
          .map { _ => () }
      }

      if (exchange.`type`.isDefined) {
        futureToReturn = dbConfig.db.run(exchanges.filter(_.id === id).map(_.`type`).update(exchange.`type`.get))
          .map { _ => () }
      }

      if (exchange.amount.isDefined) {
        futureToReturn = dbConfig.db.run(exchanges.filter(_.id === id).map(_.amount).update(exchange.amount.get))
          .map { _ => () }
      }

      futureToReturn
    }
  }

  def delete(userEmail: String, id: Int): Future[Unit] = {
    // we first verify that the asked exchange (id) to delete belongs to this user or exists
    dbConfig.db.run(exchanges.join(userDAO.users).on(_.userId === _.id).filter(_._2.email === userEmail)
      .filter(_._1.id === id).map(_._1.exchangeInfo).result.head).map { _ =>
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
