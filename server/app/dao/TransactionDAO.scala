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

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}

class TransactionDAO @Inject()(@NamedDatabase(Const.DbName) dbConfigProvider: DatabaseConfigProvider,
                               userDAO: UserDAO, budgetDAO: BudgetDAO)
                              (implicit executionContext: ExecutionContext) {
  private val dbConfig: DatabaseConfig[JdbcProfile] = dbConfigProvider.get[JdbcProfile]
  // initialisation of foreign key in SQLite
  dbConfig.db.run(DBIO.seq(sqlu"PRAGMA foreign_keys = ON;")).map { _ => () }

  private val transactions: TableQuery[TransactionTable] = TableQuery[TransactionTable]

  def insert(userEmail: String, transaction: TransactionPOSTDTO): Future[Future[Unit]] = {
    var userId: Int = 0

    // we get the id of the connected user
    // we need this value before to continue
    Await.ready(userDAO.getId(userEmail).map { id =>
      userId = id
    }, Duration(Const.maxTimeToWaitInSeconds, Const.timeToWaitUnit))

    // we check that the specified budgetId exists for this user
    budgetDAO.find(userEmail, transaction.budgetId).map { budget =>
      // we check that a positive number for the amount is associated to an income and inversely

      // TODO : TESTS
      if (transaction.amount < 0 && budget.`type` == "income") {
        throw new Exception("transaction amount negative associated to an income budget")
      }

      if (transaction.amount > 0 && budget.`type` == "outcome") {
        throw new Exception("transaction amount positive associated to an outcome budget")
      }

      var dateToInsert: Date = null

      // test if a date is given or not (None value or not)
      if (transaction.date.isEmpty) {
        // we get the actual date if it is not given by the client
        dateToInsert = Date.valueOf(Const.format.format(Calendar.getInstance().getTime))
      }
      else {
        dateToInsert = Date.valueOf(transaction.date.get.year + "-" + transaction.date.get.month +
          "-" + transaction.date.get.day)
      }

      val transactionToInsert = Transaction(transaction.name, dateToInsert, transaction.amount, userId,
        transaction.budgetId)

      dbConfig.db.run(transactions += transactionToInsert).map { _ => () }
    }
  }

  def findAll(userEmail: String, dates: FromToDatesDTO): Future[Seq[TransactionAllGETDTO]] = {
    // we get all the transactions for the connected user (email)
    dbConfig.db.run(transactions.join(userDAO.users).on(_.userId === _.id).filter(_._2.email === userEmail)
      .map(_._1.transactionInfo).result).map { transactions =>
      // if from and to dates are presents (JSON), we keep only the corresponding transactions
      // if from and to are invalid (from after to), we show nothing (return)
      val transactionsToSendToKeep = transactions.filter { t =>
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

      val transactionsToSend = transactionsToSendToKeep.map { t =>
        val dateToSend = Option(DateDTO(t.date.toString.substring(8, 10).toInt, t.date.toString.substring(5, 7).toInt,
          t.date.toString.substring(0, 4).toInt))
        var budgetName: String = ""

        // we need this value before to continue
        Await.ready(budgetDAO.getBudgetName(t.budgetId).map { name =>
          budgetName = name
        }, Duration(Const.maxTimeToWaitInSeconds, Const.timeToWaitUnit))

        val budgetToSend = TransactionBudgetGETDTO(t.budgetId, budgetName)
        val transactionToSend = TransactionAllGETDTO(t.id, t.name, dateToSend, budgetToSend, t.amount)

        transactionToSend
      }

      transactionsToSend
    }
  }

  def find(userEmail: String, id: Int): Future[TransactionGETDTO] = {
    // with a join and the email of the connected user, we first verify that the asked transaction (id) belongs to this user
    // or exists
    dbConfig.db.run(transactions.join(userDAO.users).on(_.userId === _.id).filter(_._2.email === userEmail)
      .filter(_._1.id === id).map(_._1.transactionInfo).result.head).map { transaction =>
      val dateToSend = Option(DateDTO(transaction.date.toString.substring(8, 10).toInt,
        transaction.date.toString.substring(5, 7).toInt, transaction.date.toString.substring(0, 4).toInt))
      var budgetName: String = ""

      // we need this value before to continue
      Await.ready(budgetDAO.getBudgetName(transaction.budgetId).map { name =>
        budgetName = name
      }, Duration(Const.maxTimeToWaitInSeconds, Const.timeToWaitUnit))

      val budgetToSend = TransactionBudgetGETDTO(transaction.budgetId, budgetName)
      val transactionToSend = TransactionGETDTO(transaction.name, dateToSend, budgetToSend, transaction.amount)

      transactionToSend
    }
  }

  def update(userEmail: String, id: Int, transaction: TransactionPATCHDTO): Future[Future[Unit]] = {
    // we first verify that the asked transaction (id) to update belongs to this user or exists
    dbConfig.db.run(transactions.join(userDAO.users).on(_.userId === _.id).filter(_._2.email === userEmail)
      .filter(_._1.id === id).result.head).map { _ =>
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

      if (transaction.budgetId.isDefined) {
        var budgetExist: Boolean = true

        // we check that the new budget exists
        Await.ready(budgetDAO.isBudgetExisting(userEmail, transaction.budgetId.get).map { r =>
          r.map { v =>
            budgetExist = v
          }
        }, Duration(Const.maxTimeToWaitInSeconds, Const.timeToWaitUnit))

        if (!budgetExist) {
          throw new Exception("new budget doesn't exist")
        }

        // we retrieve the type of the budgets
        
        Await.ready(budgetDAO.isBudgetExisting(userEmail, transaction.budgetId.get).map { r =>
          r.map { v =>
            budgetExist = v
          }
        }, Duration(Const.maxTimeToWaitInSeconds, Const.timeToWaitUnit))

        if (transaction.amount < 0 && budget.`type` == "income") {
          throw new Exception("transaction amount negative associated to an income budget")
        }

        if (transaction.amount > 0 && budget.`type` == "outcome") {
          throw new Exception("transaction amount positive associated to an outcome budget")
        }
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
      .filter(_._1.id === id).result.head).map { _ =>
      dbConfig.db.run(transactions.filter(_.id === id).delete).map { _ => () }
    }
  }

  private class TransactionTable(tag: Tag) extends Table[Transaction](tag, "transaction") {
    def id: Rep[Int] = column[Int]("id", O.PrimaryKey, O.AutoInc)
    def userId: Rep[Int] = column[Int]("userId")
    def budgetId: Rep[Int] = column[Int]("budgetId")
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
    // n to one relationship
    def budget: ForeignKeyQuery[budgetDAO.BudgetTable, Budget] = {
      foreignKey("budget_FK", budgetId, budgetDAO.budgets)(_.id, onDelete = ForeignKeyAction.Cascade,
        onUpdate = ForeignKeyAction.Cascade)
    }

    def * : ProvenShape[Transaction] = {
      (name, date, amount, userId, budgetId) <> ((Transaction.apply _).tupled, Transaction.unapply)
    }

    def transactionInfo: MappedProjection[TransactionGET, (Int, String, Date, Int, Double)] = {
      (id, name, date, budgetId, amount) <> (TransactionGET.tupled, TransactionGET.unapply)
    }
  }
}
