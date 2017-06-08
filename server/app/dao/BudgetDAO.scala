package dao

import javax.inject.Inject

import models._
import org.sqlite.SQLiteException
import play.api.db.slick.DatabaseConfigProvider
import play.api.libs.json.Json
import play.db.NamedDatabase
import slick.backend.DatabaseConfig
import slick.driver.JdbcProfile
import slick.driver.SQLiteDriver.api._
import slick.lifted.{ForeignKeyQuery, Index, MappedProjection, ProvenShape}
import utils.Const

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}

class BudgetDAO @Inject()(@NamedDatabase(Const.DbName) dbConfigProvider: DatabaseConfigProvider, val userDAO: UserDAO)
                         (implicit executionContext: ExecutionContext) {
  private val dbConfig: DatabaseConfig[JdbcProfile] = dbConfigProvider.get[JdbcProfile]
  dbConfig.db.run(DBIO.seq(sqlu"PRAGMA foreign_keys = ON;")).map { _ => () }

  val budgets: TableQuery[BudgetTable] = TableQuery[BudgetTable]
  private val takesFromBudgets: TableQuery[TakesFromTable] = TableQuery[TakesFromTable]

  def getBudgetName(id: Int): Future[String] = dbConfig.db.run(budgets.filter(_.id === id).map(_.name).result.head)

  def isBudgetExisting(userEmail: String, id: Int): Future[Future[Boolean]] = {
    // we get the id of the connected user
    userDAO.getId(userEmail).map { userId =>
      dbConfig.db.run(budgets.filter(_.userId === userId).filter(_.id === id).exists.result)
    }
  }

  def insertBudget(userEmail: String, budget: BudgetPOSTDTO): Future[Future[Int]] = {
    // we get the id of the connected user
    userDAO.getId(userEmail).map { userId =>
      val budgetToInsert = Budget(budget.name, budget.`type`, budget.used, budget.left, budget.exceeding,
        budget.persistent, budget.reported, budget.color, userId)

      dbConfig.db.run(budgets returning budgets.map(_.id) += budgetToInsert)
    }
  }

  def insertTakesFrom(budgetGoesToId: Int, takesFrom: TakesFromDTO): Future[Unit] = {
    // we check that the two budgets are different
    if (budgetGoesToId == takesFrom.budgetId) {
      Future.failed(new Exception("can't specify the same budget twice"))
    }
    // we verify that the specified takesFrom budget is an income budget
    else {
      dbConfig.db.run(budgets.filter(_.id === takesFrom.budgetId).map(_.`type`).result.head).map { t =>
        if (t.equals("income")) {
          val takesFromToInsert = TakesFrom(takesFrom.order, budgetGoesToId, takesFrom.budgetId)

          // we must wait the result
          Await.result(dbConfig.db.run(takesFromBudgets += takesFromToInsert).map { _ => () },
            Duration(Const.maxTimeToWaitInSeconds, Const.timeToWaitUnit))
        }
        else {
          throw new Exception("can't specify a takesFrom budget of type outcome")
        }
      }
    }
  }

  // TODO: make better error messages (improvement)

  def insert(userEmail: String, budget: BudgetPOSTDTO): Future[Unit] = {
    // test if takesFrom is defined or not (if we must or not add entries in table takes_from)
    if (budget.takesFrom.isDefined) {
      // it is an error to define a takesFrom tab with the type income for the budget to create
      if (budget.`type`.equals("income")) {
        return Future.failed(new Exception("takesFrom hast to be define only for outcome budgets"))
      }
      else {
        var errorWithTakesFrom: Boolean = false
        var insertedBudgetId: Int = 0

        // insert the budget and get the id
        // we wait for the result
        Await.ready(insertBudget(userEmail, budget).map { f =>
          Await.ready(f.map { id =>
            insertedBudgetId = id
          }, Duration(Const.maxTimeToWaitInSeconds, Const.timeToWaitUnit))
        }, Duration(Const.maxTimeToWaitInSeconds, Const.timeToWaitUnit))

        // verify that the specified budgets (id) in takesFrom field exist among the budgets of this user
        budget.takesFrom.get.foreach { b =>
          // we must wait
          Await.ready(isBudgetExisting(userEmail, b.budgetId).map { r =>
            Await.ready(r.map { v =>
              if (!errorWithTakesFrom) {
                errorWithTakesFrom = !v
              }

              // the insertion in takes_from table is made only if the specified budget exists (field takesFrom)
              // and if the conditions are respected
              if (v) {
                Await.ready(insertTakesFrom(insertedBudgetId, b).recover {
                  case e: SQLiteException if e.getResultCode.code == Const.SQLiteUniqueConstraintErrorCode =>
                    errorWithTakesFrom = true
                  case _: Exception => errorWithTakesFrom = true
                }, Duration(Const.maxTimeToWaitInSeconds, Const.timeToWaitUnit))
              }
            }, Duration(Const.maxTimeToWaitInSeconds, Const.timeToWaitUnit))
          }, Duration(Const.maxTimeToWaitInSeconds, Const.timeToWaitUnit))
        }

        if (errorWithTakesFrom) {
          return Future.failed(new Exception("budget '%s' created with one or more error(s) for takesFrom values"))
        }

        // all is right
        return Future.successful(())
      }
    }

    // simply add budget
    insertBudget(userEmail, budget).map { _ => () }
  }

  def findAll(userEmail: String): Future[Seq[BudgetAndTakesFromAllGETDTO]] = {
    val budgetsToReturn: ArrayBuffer[BudgetAndTakesFromAllGETDTO] = ArrayBuffer()

    dbConfig.db.run(budgets.join(userDAO.users).on(_.userId === _.id).filter(_._2.email === userEmail)
      .map(_._1.budgetInfo).result).map { retrievedBudgets =>
      retrievedBudgets.foreach { b =>
        // we need to wait that requests are completed
        Await.ready(dbConfig.db.run(takesFromBudgets.filter(_.budgetGoesToId === b.id).map(_.takesFromInfo).result).map { t =>
          budgetsToReturn += BudgetAndTakesFromAllGETDTO(b.id, b.name, b.`type`, b.used, b.left, b.exceeding,
            b.persistent, b.reported, b.color, Option(t))
        }, Duration(Const.maxTimeToWaitInSeconds, Const.timeToWaitUnit))
      }

      budgetsToReturn
    }
  }

  def find(userEmail: String, id: Int): Future[BudgetAndTakesFromGETDTO] = {
    var budgetToReturn: BudgetAndTakesFromGETDTO = null

    // with a join and the email of the connected user, we first verify that the asked budgets (id) belongs to this user
    // or exists
    dbConfig.db.run(budgets.join(userDAO.users).on(_.userId === _.id).filter(_._2.email === userEmail)
      .filter(_._1.id === id).map(_._1.budgetInfo).result.head).map { retrievedBudget =>
      // we need to wait that request is completed
      Await.ready(dbConfig.db.run(takesFromBudgets.filter(_.budgetGoesToId === retrievedBudget.id)
        .map(_.takesFromInfo).result).map { t =>
        budgetToReturn = BudgetAndTakesFromGETDTO(retrievedBudget.name, retrievedBudget.`type`, retrievedBudget.used,
          retrievedBudget.left, retrievedBudget.exceeding, retrievedBudget.persistent, retrievedBudget.reported,
          retrievedBudget.color, Option(t))
      }, Duration(Const.maxTimeToWaitInSeconds, Const.timeToWaitUnit))

      budgetToReturn
    }
  }

  def update(userEmail: String, id: Int, budget: BudgetPATCHDTO): Future[Any] = {
    // we first verify that the asked budget (id) to update belongs to this user or exists
    dbConfig.db.run(budgets.join(userDAO.users).on(_.userId === _.id).filter(_._2.email === userEmail)
      .filter(_._1.id === id).result.head).map { _ =>
      // we update only the present fields
      // not the value None
      if (budget.name.isDefined) {
        dbConfig.db.run(budgets.filter(_.id === id).map(_.name).update(budget.name.get)).map { _ => () }
      }

      if (budget.used.isDefined) {
        dbConfig.db.run(budgets.filter(_.id === id).map(_.used).update(budget.used.get)).map { _ => () }
      }

      if (budget.left.isDefined) {
        dbConfig.db.run(budgets.filter(_.id === id).map(_.left).update(budget.left.get)).map { _ => () }
      }

      if (budget.exceeding.isDefined) {
        dbConfig.db.run(budgets.filter(_.id === id).map(_.exceeding).update(budget.exceeding.get)).map { _ => () }
      }

      if (budget.persistent.isDefined) {
        dbConfig.db.run(budgets.filter(_.id === id).map(_.persistent).update(budget.persistent.get)).map { _ => () }
      }

      if (budget.reported.isDefined) {
        dbConfig.db.run(budgets.filter(_.id === id).map(_.reported).update(budget.reported.get)).map { _ => () }
      }

      if (budget.color.isDefined) {
        dbConfig.db.run(budgets.filter(_.id === id).map(_.color).update(budget.color.get)).map { _ => () }
      }
    }
  }

  def delete(userEmail: String, id: Int): Future[Future[Unit]] = {
    // we first verify that the asked exchange (id) to delete belongs to this user or exists
    dbConfig.db.run(budgets.join(userDAO.users).on(_.userId === _.id).filter(_._2.email === userEmail)
      .filter(_._1.id === id).result.head).map { _ =>
      dbConfig.db.run(budgets.filter(_.id === id).delete).map { _ => () }
    }
  }

  class BudgetTable(tag: Tag) extends Table[Budget](tag, "budget") {
    def id: Rep[Int] = column[Int]("id", O.PrimaryKey, O.AutoInc)
    def userId: Rep[Int] = column[Int]("userId")
    def name: Rep[String] = column[String]("name")
    def `type`: Rep[String] = column[String]("type")
    def used: Rep[Double] = column[Double]("used")
    def left: Rep[Double] = column[Double]("left")
    def exceeding: Rep[Double] = column[Double]("exceeding")
    def persistent: Rep[Int] = column[Int]("persistent")
    def reported: Rep[Boolean] = column[Boolean]("reported")
    def color: Rep[String] = column[String]("color")

    // A reified foreign key relation to an user that can be navigated to create a join
    // n to one relationship
    def user: ForeignKeyQuery[userDAO.UserTable, User] = {
      // when an user is deleted, his budgets are also deleted (same with update)
      foreignKey("user_FK", userId, userDAO.users)(_.id, onDelete = ForeignKeyAction.Cascade,
        onUpdate = ForeignKeyAction.Cascade)
    }

    def * : ProvenShape[Budget] = {
      (name, `type`, used, left, exceeding, persistent, reported, color, userId) <>
        ((Budget.apply _).tupled, Budget.unapply)
    }

    def budgetInfo: MappedProjection[BudgetGET, (Int, String, String, Double, Double, Double, Int, Boolean, String)] = {
      (id, name, `type`, used, left, exceeding, persistent, reported, color) <> (BudgetGET.tupled, BudgetGET.unapply)
    }
  }

  private class TakesFromTable(tag: Tag) extends Table[TakesFrom](tag, "takes_from") {
    def id: Rep[Int] = column[Int]("id", O.PrimaryKey, O.AutoInc)
    def budgetGoesToId: Rep[Int] = column[Int]("budgetGoesToId")
    def budgetTakesFromId: Rep[Int] = column[Int]("budgetTakesFromId")
    def order: Rep[Int] = column[Int]("order")

    // n to one relationship
    def budgetGoesTo: ForeignKeyQuery[BudgetTable, Budget] = {
      // when an budget is deleted, his potential budgets "takes from" are also deleted (same with update)
      foreignKey("budgetGoesTo_FK", budgetGoesToId, budgets)(_.id, onDelete = ForeignKeyAction.Cascade,
        onUpdate = ForeignKeyAction.Cascade)
    }
    // n to one relationship
    def budgetTakesFrom: ForeignKeyQuery[BudgetTable, Budget] = {
      // when an budget is deleted, his potential budgets "takes from" are also deleted (same with update)
      foreignKey("budgetTakesFrom_FK", budgetTakesFromId, budgets)(_.id, onDelete = ForeignKeyAction.Cascade,
        onUpdate = ForeignKeyAction.Cascade)
    }

    // make a row unique
    def rowIndex: Index = index("unique_row", (budgetGoesToId, budgetTakesFromId), unique = true)

    def * : ProvenShape[TakesFrom] = {
      (order, budgetGoesToId, budgetTakesFromId) <> ((TakesFrom.apply _).tupled, TakesFrom.unapply)
    }

    def takesFromInfo: MappedProjection[TakesFromDTO, (Int, Int)] = (order, budgetTakesFromId) <>
      ((TakesFromDTO.apply _).tupled, TakesFromDTO.unapply)
  }
}
