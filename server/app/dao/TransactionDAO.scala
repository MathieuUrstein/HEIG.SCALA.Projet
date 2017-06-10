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

import scala.collection.immutable.ListMap
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}

class TransactionDAO @Inject()(@NamedDatabase(Const.DbName) dbConfigProvider: DatabaseConfigProvider,
                               val userDAO: UserDAO, val budgetDAO: BudgetDAO)
                              (implicit executionContext: ExecutionContext) {
  private val dbConfig: DatabaseConfig[JdbcProfile] = dbConfigProvider.get[JdbcProfile]
  // initialisation of foreign keys in SQLite
  dbConfig.db.run(DBIO.seq(sqlu"PRAGMA foreign_keys = ON;")).map { _ => () }

  private val transactions: TableQuery[TransactionTable] = TableQuery[TransactionTable]

  def updateBudgetIncome(budgetId: Int, amount: Double): Future[Unit] = {
    var usedActualValue: Double = 0
    var leftActualValue: Double = 0
    var exceedingActualValue: Double = 0

    // we need these values before to continue
    Await.ready(dbConfig.db.run(budgetDAO.budgets.filter(_.id === budgetId).map(_.budgetTransactionInfo)
      .result.head).map { v =>
      usedActualValue = v._1
      leftActualValue = v._2
      exceedingActualValue = v._3
    }, Duration(Const.maxTimeToWaitInSeconds, Const.timeToWaitUnit))

    // we check if we have a positive exceeding
    if (usedActualValue == 0) {
      // it is a positive exceeding
      val newExceedingValue = amount + exceedingActualValue

      // we update the exceeding
      dbConfig.db.run(budgetDAO.budgets.filter(_.id === budgetId).map(_.exceeding).update(newExceedingValue))
        .map { _ => () }
    }
    else {
      val newUsedActualValue = usedActualValue - amount

      if (newUsedActualValue < 0) {
        // it is a positive exceeding
        val newExceedingValue = -newUsedActualValue
        // we put the left value to max
        val newLeftValue = leftActualValue + usedActualValue

        // we update the exceeding
        // we put the used value to 0
        // we put the left value to max
        dbConfig.db.run(budgetDAO.budgets.filter(_.id === budgetId).map(budget => (budget.exceeding, budget.used, budget.left))
          .update(newExceedingValue, 0, newLeftValue)).map { _ => () }
      }
      else {
        // we change the left value
        val newLeftValue = leftActualValue + amount

        // we update the new used value
        // we change the left value
        dbConfig.db.run(budgetDAO.budgets.filter(_.id === budgetId).map(budget => (budget.used, budget.left))
          .update(newUsedActualValue, newLeftValue)).map { _ => () }
      }
    }
  }

  def updateBudgetOutcome(budgetId: Int, amount: Double): Future[Unit] = {
    var usedActualValue: Double = 0
    var leftActualValue: Double = 0
    var exceedingActualValue: Double = 0

    // we need these values before to continue
    Await.ready(dbConfig.db.run(budgetDAO.budgets.filter(_.id === budgetId).map(_.budgetTransactionInfo)
      .result.head).map { v =>
      usedActualValue = v._1
      leftActualValue = v._2
      exceedingActualValue = v._3
    }, Duration(Const.maxTimeToWaitInSeconds, Const.timeToWaitUnit))

    // we check if we are in exceeding
    if (leftActualValue == 0) {
      // it is a negative exceeding
      val newExceedingValue = exceedingActualValue + (-amount)

      // only update exceeding value
      dbConfig.db.run(budgetDAO.budgets.filter(_.id === budgetId).map(_.exceeding).update(newExceedingValue))
        .map { _ => () }
    }
    else {
      val newLeftActualValue = leftActualValue + amount

      if (newLeftActualValue < 0) {
        // it is a negative exceeding
        val newExceedingValue = -newLeftActualValue
        // we update the used value to max
        val newUsedValue = leftActualValue + usedActualValue

        // we update the exceeding
        // we put the left value to 0
        // we update the used value to max
        dbConfig.db.run(budgetDAO.budgets.filter(_.id === budgetId).map(budget => (budget.exceeding, budget.left, budget.used))
          .update(newExceedingValue, 0, newUsedValue)).map { _ => () }
      }
      else {
        // we change the used value
        val newUsedValue = usedActualValue + (-amount)

        // we update the new left value
        // we change the used value
        dbConfig.db.run(budgetDAO.budgets.filter(_.id === budgetId).map(budget => (budget.left, budget.used))
          .update(newLeftActualValue, newUsedValue)).map { _ => () }
      }
    }
  }

  def updateBudgetIncomeAsOutcome(budgetId: Int, amount: Double): Double = {
    var usedActualValue: Double = 0
    var leftActualValue: Double = 0
    var exceedingActualValue: Double = 0

    // we need these values before to continue
    Await.ready(dbConfig.db.run(budgetDAO.budgets.filter(_.id === budgetId).map(_.budgetTransactionInfo)
      .result.head).map { v =>
      usedActualValue = v._1
      leftActualValue = v._2
      exceedingActualValue = v._3
    }, Duration(Const.maxTimeToWaitInSeconds, Const.timeToWaitUnit))

    // we check if we are in exceeding
    if (leftActualValue == 0) {
      // it is a negative exceeding
      // we do nothing (we will eventually look for the next Income budget to take money from)
      amount
    }
    else {
      // if we have an exceeding
      if (exceedingActualValue > 0) {
        var newExceedingValue: Double = 0

        newExceedingValue = exceedingActualValue + amount

        if (newExceedingValue >= 0) {
          // we update only the exceeding value
          Await.ready(dbConfig.db.run(budgetDAO.budgets.filter(_.id === budgetId).map(_.exceeding).update(newExceedingValue))
            .map { _ => () }, Duration(Const.maxTimeToWaitInSeconds, Const.timeToWaitUnit))

          0.0
        }
        else {
          // we update the exceeding to 0
          Await.ready(dbConfig.db.run(budgetDAO.budgets.filter(_.id === budgetId).map(_.exceeding).update(0))
            .map { _ => () }, Duration(Const.maxTimeToWaitInSeconds, Const.timeToWaitUnit))

          val newLeftValue = leftActualValue + newExceedingValue

          if (newLeftValue < 0) {
            // we update the used value to max
            val newUsedValue = leftActualValue + usedActualValue

            // we put the left value to 0
            // we update the used value to max
            Await.ready(dbConfig.db.run(budgetDAO.budgets.filter(_.id === budgetId).map(budget => (budget.left, budget.used))
              .update(0, newUsedValue)).map { _ => () }, Duration(Const.maxTimeToWaitInSeconds, Const.timeToWaitUnit))

            // we will report the exceeding on the eventually next Income budget to take money from
            newLeftValue
          }
          else {
            // we change the used value
            val newUsedValue = usedActualValue + (-newExceedingValue)

            // we update the new left value
            // we update the used value
            Await.ready(dbConfig.db.run(budgetDAO.budgets.filter(_.id === budgetId).map(budget => (budget.left, budget.used))
              .update(newLeftValue, newUsedValue)).map { _ => () }, Duration(Const.maxTimeToWaitInSeconds, Const.timeToWaitUnit))

            0.0
          }
        }
      }
      else {
        val newLeftValue = leftActualValue + amount

        if (newLeftValue < 0) {
          // we update the used value to max
          val newUsedValue = leftActualValue + usedActualValue

          // we put the left value to 0
          // we update the used value to max
          Await.ready(dbConfig.db.run(budgetDAO.budgets.filter(_.id === budgetId).map(budget => (budget.left, budget.used))
            .update(0, newUsedValue)).map { _ => () }, Duration(Const.maxTimeToWaitInSeconds, Const.timeToWaitUnit))

          // we will report the exceeding on the eventually next Income budget to take money from
          newLeftValue
        }
        else {
          // we change the used value
          val newUsedValue = usedActualValue + (-amount)

          // we update the new left value
          // we update the used value
          Await.ready(dbConfig.db.run(budgetDAO.budgets.filter(_.id === budgetId).map(budget => (budget.left, budget.used))
            .update(newLeftValue, newUsedValue)).map { _ => () }, Duration(Const.maxTimeToWaitInSeconds, Const.timeToWaitUnit))

          0.0
        }
      }
    }
  }

  def insert(userEmail: String, transaction: TransactionPOSTDTO): Future[Any] = {
    var userId: Int = 0

    // we get the id of the connected user
    // we need this value before to continue
    Await.ready(userDAO.getId(userEmail).map { id =>
      userId = id
    }, Duration(Const.maxTimeToWaitInSeconds, Const.timeToWaitUnit))

    // we check that the specified budgetId exists for this user
    budgetDAO.find(userEmail, transaction.budgetId).map { budget =>
      // we check that a positive number for the amount is associated to an Income and inversely
      if (transaction.amount < 0 && budget.`type` == "Income") {
        throw new Exception("transaction amount negative associated to an Income budget")
      }

      if (transaction.amount > 0 && budget.`type` == "Outcome") {
        throw new Exception("transaction amount positive associated to an Outcome budget")
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


      // we update the corresponding budget (left, used and exceeding values)
      if (budget.`type` == "Income") {
        updateBudgetIncome(transaction.budgetId, transaction.amount)
      }
      else {
        updateBudgetOutcome(transaction.budgetId, transaction.amount)

        var budgetsMap: Map[Int, Int] = Map()

        // we look for the order to treat the Income budgets
        budget.takesFrom.get.foreach { b =>
          budgetsMap += b.order -> b.budgetId
        }

        val sortedMap = ListMap(budgetsMap.toSeq.sortWith(_._1 < _._1):_*)
        var returnedExceeding: Double = transaction.amount

        // TODO: add debt when all incomes are exhausted (improvement)

        // we stop when we have a null exceeding or all Income budgets have been processed
        sortedMap.takeWhile(_ => returnedExceeding != 0).foreach { e =>
          // updates takesFrom budgets (Income) in order
          // Income budgets are considered as Outcome
          returnedExceeding = updateBudgetIncomeAsOutcome(e._2, returnedExceeding)
        }
      }
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
        val dateToSend = DateDTO(t.date.toString.substring(8, 10).toInt, t.date.toString.substring(5, 7).toInt,
          t.date.toString.substring(0, 4).toInt)
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
      val dateToSend = DateDTO(transaction.date.toString.substring(8, 10).toInt,
        transaction.date.toString.substring(5, 7).toInt, transaction.date.toString.substring(0, 4).toInt)
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

  // TODO: when updating the amount field or delete a transaction, update the corresponding budgets (improvement)

  def update(userEmail: String, id: Int, transaction: TransactionPATCHDTO): Future[Any] = {
    // we first verify that the asked transaction (id) to update belongs to this user or exists
    dbConfig.db.run(transactions.join(userDAO.users).on(_.userId === _.id).filter(_._2.email === userEmail)
      .filter(_._1.id === id).result.head).map { _ =>
      // we update only the present fields
      // not the value None
      if (transaction.name.isDefined) {
        dbConfig.db.run(transactions.filter(_.id === id).map(_.name).update(transaction.name.get)).map { _ => () }
      }

      if (transaction.date.isDefined) {
        val dateToInsert = Date.valueOf(transaction.date.get.year + "-" + transaction.date.get.month +
          "-" + transaction.date.get.day)

        dbConfig.db.run(transactions.filter(_.id === id).map(_.date).update(dateToInsert)).map { _ => () }
      }

      if (transaction.amount.isDefined) {
        dbConfig.db.run(transactions.filter(_.id === id).map(_.amount).update(transaction.amount.get)).map { _ => () }
      }

      if (transaction.budgetId.isDefined) {
        // we wait if an error is coming for the new budget
        var budgetExist: Boolean = true

        // we check that the new budget exists
        Await.ready(budgetDAO.isBudgetExisting(userEmail, transaction.budgetId.get).map { r =>
          Await.ready(r.map { v =>
            budgetExist = v
          }, Duration(Const.maxTimeToWaitInSeconds, Const.timeToWaitUnit))
        }, Duration(Const.maxTimeToWaitInSeconds, Const.timeToWaitUnit))

        if (!budgetExist) {
          throw new Exception("new budget doesn't exist, budget not updated")
        }

        var budgetType: String = ""

        // we retrieve the type of the new budget to makes checks
        Await.ready(budgetDAO.find(userEmail, transaction.budgetId.get).map { b =>
          budgetType = b.`type`
        }, Duration(Const.maxTimeToWaitInSeconds, Const.timeToWaitUnit))

        // check for the association
        if (transaction.amount.get < 0 && budgetType == "Income") {
          throw new Exception("transaction amount negative associated to an Income budget, budget not updated")
        }

        if (transaction.amount.get > 0 && budgetType == "Outcome") {
          throw new Exception("transaction amount positive associated to an Outcome budget, budget not updated")
        }

        dbConfig.db.run(transactions.filter(_.id === id).map(_.budgetId).update(transaction.budgetId.get)).map { _ => () }
      }
    }
  }

  def delete(userEmail: String, id: Int): Future[Future[Unit]] = {
    // we first verify that the asked transaction (id) to delete belongs to this user or exists
    dbConfig.db.run(transactions.join(userDAO.users).on(_.userId === _.id).filter(_._2.email === userEmail)
      .filter(_._1.id === id).map(_._1.transactionInfo).result.head).map { transaction =>
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
