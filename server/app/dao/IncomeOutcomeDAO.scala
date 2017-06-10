package dao

import java.sql.Date
import javax.inject.Inject

import models.{IncomeOutcome, User}
import play.api.db.slick.DatabaseConfigProvider
import play.db.NamedDatabase
import slick.backend.DatabaseConfig
import slick.driver.JdbcProfile
import slick.driver.SQLiteDriver.api._
import slick.lifted.{ForeignKeyQuery, ProvenShape}
import utils.Const

import scala.concurrent.{ExecutionContext, Future}

class IncomeOutcomeDAO @Inject()(@NamedDatabase(Const.DbName) dbConfigProvider: DatabaseConfigProvider,
                                 val userDAO: UserDAO)(implicit executionContext: ExecutionContext) {
  private val dbConfig: DatabaseConfig[JdbcProfile] = dbConfigProvider.get[JdbcProfile]
  // initialisation of foreign keys in SQLite
  dbConfig.db.run(DBIO.seq(sqlu"PRAGMA foreign_keys = ON;")).map { _ => () }

  private val incomesOutcomes: TableQuery[IncomeOutcomeTable] = TableQuery[IncomeOutcomeTable]

  def insert(incomeOutcome: IncomeOutcome): Future[Unit] = {
    dbConfig.db.run(incomesOutcomes += incomeOutcome).map { _ => () }
  }

  def findAll(userEmail: String): Future[Seq[IncomeOutcome]] = {
    dbConfig.db.run(incomesOutcomes.join(userDAO.users).on(_.userId === _.id).filter(_._2.email === userEmail)
      .map(_._1).result)
  }

  private class IncomeOutcomeTable(tag: Tag) extends Table[IncomeOutcome](tag, "income_outcome") {
    def id: Rep[Int] = column[Int]("id", O.PrimaryKey, O.AutoInc)
    def userId: Rep[Int] = column[Int]("userId")
    def date: Rep[Date] = column[Date]("date")
    def income: Rep[Double] = column[Double]("income")
    def outcome: Rep[Double] = column[Double]("outcome")

    // A reified foreign key relation to an user that can be navigated to create a join
    // n to one relationship
    def user: ForeignKeyQuery[userDAO.UserTable, User] = {
      // when an user is deleted, his income and outcome values are also deleted (same with update)
      foreignKey("user_FK", userId, userDAO.users)(_.id, onDelete = ForeignKeyAction.Cascade,
        onUpdate = ForeignKeyAction.Cascade)
    }

    def * : ProvenShape[IncomeOutcome] = {
      (date, income, outcome, userId) <> ((IncomeOutcome.apply _).tupled, IncomeOutcome.unapply)
    }
  }
}
