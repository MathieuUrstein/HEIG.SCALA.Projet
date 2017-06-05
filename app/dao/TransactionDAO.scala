package dao

import java.sql.Date
import java.util.{Calendar, NoSuchElementException}
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

class TransactionDAO @Inject()(@NamedDatabase(Const.DbName) dbConfigProvider: DatabaseConfigProvider, userDAO: UserDAO)
                              (implicit executionContext: ExecutionContext) {
  private val dbConfig: DatabaseConfig[JdbcProfile] = dbConfigProvider.get[JdbcProfile]

  private val transactions: TableQuery[TransactionTable] = TableQuery[TransactionTable]

  def insert(userEmail: String, transaction: TransactionPOSTDTO): Future[Future[Unit]] = {
    // we get the id of the connected user
    userDAO.getId(userEmail).map { userId =>
      var dateToInsert: Date = null

      // test if a date is given or not (None value or not)
      if (transaction.date.isEmpty) {
        // we get the actual date if we don't give one
        dateToInsert = Date.valueOf(Const.format.format(Calendar.getInstance().getTime))
      }
      else {
        dateToInsert = Date.valueOf(transaction.date.get.year + "-" + transaction.date.get.month +
          "-" + transaction.date.get.day)
      }

      val transactionToInsert = Transaction(transaction.name, dateToInsert, transaction.amount, userId)

      dbConfig.db.run(transactions += transactionToInsert).map { _ => () }
    }
  }

  def findAll(userEmail: String): Future[Seq[TransactionGET]] = {
    // we get all the transactions for the connected user (email)
    dbConfig.db.run(transactions.join(userDAO.users).on(_.userId === _.id).filter(_._2.email === userEmail)
      .map(_._1.transactionInfo).result)
  }

  def find(userEmail: String, id: Int): Future[TransactionGET] = {
    // with a join and the email of the connected user, we first verify that the asked transaction (id) belongs to this user
    // or exists
    dbConfig.db.run(transactions.join(userDAO.users).on(_.userId === _.id).filter(_._2.email === userEmail)
      .filter(_._1.id === id).map(_._1.transactionInfo).result.head)
  }

  def update(userEmail: String, id: Int, transaction: TransactionPATCHDTO): Future[Future[Unit]] = {
    // we first verify that the asked transaction (id) to update belongs to this user or exists
    dbConfig.db.run(transactions.join(userDAO.users).on(_.userId === _.id).filter(_._2.email === userEmail)
      .filter(_._1.id === id).map(_._1.transactionInfo).result.head).map { _ =>
      // default future with success and do nothing
      var futureToReturn = Future.successful(())

      // we update only the present fields
      // not the value None
      if (transaction.name.isDefined) {
        futureToReturn = dbConfig.db.run(transactions.filter(_.id === id).map(_.name).update(transaction.name.get))
        .map { _ => () }
      }

      if (transaction.date.isDefined) {
        val dateToInsert = Date.valueOf(transaction.date.get.year + "-" + transaction.date.get.month +
          "-" + transaction.date.get.day)

        futureToReturn = dbConfig.db.run(transactions.filter(_.id === id).map(_.date).update(dateToInsert))
          .map { _ => () }
      }

      if (transaction.amount.isDefined) {
        futureToReturn = dbConfig.db.run(transactions.filter(_.id === id).map(_.amount).update(transaction.amount.get))
          .map { _ => () }
      }

      futureToReturn
    }
  }

  def delete(userEmail: String, id: Int): Future[Future[Unit]] = {
    // we first verify that the asked transaction (id) to delete belongs to this user or exists
    dbConfig.db.run(transactions.join(userDAO.users).on(_.userId === _.id).filter(_._2.email === userEmail)
      .filter(_._1.id === id).map(_._1.transactionInfo).result.head).map { _ =>
      dbConfig.db.run(transactions.filter(_.id === id).delete).map { _ => () }
    }
  }

  private class TransactionTable(tag: Tag) extends Table[Transaction](tag, "transaction") {
    def id: Rep[Int] = column[Int]("id", O.PrimaryKey, O.AutoInc)
    def userId: Rep[Int] = column[Int]("userId")
    def name: Rep[String] = column[String]("name")
    def date: Rep[Date] = column[Date]("date")
    def amount: Rep[Double] = column[Double]("amount")

    // A reified foreign key relation to an user that can be navigated to create a join
    // n to one relationship
    def user: ForeignKeyQuery[userDAO.UserTable, User] = {
      // when an user is deleted, his transactions are also deleted (same with update)
      foreignKey("user_FK", userId, userDAO.users)(_.id, onDelete = ForeignKeyAction.Cascade,
        onUpdate = ForeignKeyAction.Cascade)
    }
    // TODO : ajouter foreign key sur budget correspondant n to one relation
    //def budgetId = foreignKey("budget_FK", id, suppliers)(_.id)

    def * : ProvenShape[Transaction] = {
      (name, date, amount, userId) <> ((Transaction.apply _).tupled, Transaction.unapply)
    }
    def transactionInfo: MappedProjection[TransactionGET, (Int, String, Date, Double)] = {
      (id, name, date, amount) <> (TransactionGET.tupled, TransactionGET.unapply)
    }
  }
}
