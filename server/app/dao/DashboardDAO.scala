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
import slick.jdbc.SQLActionBuilder
import utils.Const

import scala.concurrent.{ExecutionContext, Future}

class DashboardDAO @Inject()(@NamedDatabase(Const.DbName) dbConfigProvider: DatabaseConfigProvider,
                             transactionDAO: TransactionDAO, userDAO: UserDAO, budgetDAO: BudgetDAO)
                            (implicit executionContext: ExecutionContext) {
  private val dbConfig: DatabaseConfig[JdbcProfile] = dbConfigProvider.get[JdbcProfile]
  // initialisation of foreign keys in SQLite
  dbConfig.db.run(DBIO.seq(sqlu"PRAGMA foreign_keys = ON;")).map { _ => () }

  def findSpendings(userEmail: String, dates: FromToDatesDTO): Future[Vector[SpendingGETDTO]] = {
    // if from and to dates are the two presents (JSON), we adapt the SQL request to integrate these dates
    // otherwise, we don't use them if no one is present (if one of them is present, we use the actual date for the other date)
    var dateFrom: Long = 0
    var dateTo: Long = 0
    var sqlRequest: SQLActionBuilder = sql""

    if (dates.from.isDefined) {
      dateFrom = Date.valueOf(dates.from.get.year + "-" + dates.from.get.month + "-" + dates.from.get.day).getTime

      if (dates.to.isEmpty) {
        dateTo = Calendar.getInstance().getTimeInMillis
      }
      else {
        dateTo = Date.valueOf(dates.to.get.year + "-" + dates.to.get.month + "-" + dates.to.get.day).getTime
      }

      sqlRequest = sql"""SELECT "transaction".date, "budget".name, SUM("transaction".amount), "budget".color
                         FROM "budget"
                         INNER JOIN "transaction" ON "budget".id = "transaction".budgetId
                         INNER JOIN "user" ON "transaction".userId = "user".id
                         WHERE "user".email = '#$userEmail' AND "budget".type = 'outcome'
                         AND "transaction".date BETWEEN '#$dateFrom' AND '#$dateTo'
                         GROUP BY "transaction".date, "budget".id
                      """
    }
    else if (dates.to.isDefined) {
      dateTo = Date.valueOf(dates.to.get.year + "-" + dates.to.get.month + "-" + dates.to.get.day).getTime
      dateFrom = Calendar.getInstance().getTimeInMillis

      sqlRequest = sql"""SELECT "transaction".date, "budget".name, SUM("transaction".amount), "budget".color
                         FROM "budget"
                         INNER JOIN "transaction" ON "budget".id = "transaction".budgetId
                         INNER JOIN "user" ON "transaction".userId = "user".id
                         WHERE "user".email = '#$userEmail' AND "budget".type = 'outcome'
                         AND "transaction".date BETWEEN '#$dateFrom' AND '#$dateTo'
                         GROUP BY "transaction".date, "budget".id
                      """
    }
    else {
      // we don't use between and the dates
      sqlRequest = sql"""SELECT "transaction".date, "budget".name, SUM("transaction".amount), "budget".color
                         FROM "budget"
                         INNER JOIN "transaction" ON "budget".id = "transaction".budgetId
                         INNER JOIN "user" ON "transaction".userId = "user".id
                         WHERE "user".email = '#$userEmail' AND "budget".type = 'outcome'
                         GROUP BY "transaction".date, "budget".id
                      """
    }

    // too complicated request with slick directly
    dbConfig.db.run(sqlRequest.as[SpendingGET]).map { spendings =>
      val spendingsToSend = spendings.map { spending =>
        val dateToSend = DateDTO(spending.date.toString.substring(8, 10).toInt, spending.date.toString.substring(5, 7).toInt,
          spending.date.toString.substring(0, 4).toInt)

        val spendingToReturn = SpendingGETDTO(dateToSend, spending.budget, spending.amount, spending.color)

        spendingToReturn
      }

      spendingsToSend
    }
  }

  def findUsage(userEmail: String, dates: FromToDatesDTO): Future[Vector[UsageGETDTO]] = {
    // if from and to dates are the two presents (JSON), we adapt the SQL request to integrate these dates
    // otherwise, we don't use them if no one is present (if one of them is present, we use the actual date for the other date)
    var dateFrom: Long = 0
    var dateTo: Long = 0
    var sqlRequest: SQLActionBuilder = sql""

    if (dates.from.isDefined) {
      dateFrom = Date.valueOf(dates.from.get.year + "-" + dates.from.get.month + "-" + dates.from.get.day).getTime

      if (dates.to.isEmpty) {
        dateTo = Calendar.getInstance().getTimeInMillis
      }
      else {
        dateTo = Date.valueOf(dates.to.get.year + "-" + dates.to.get.month + "-" + dates.to.get.day).getTime
      }

      sqlRequest = sql"""SELECT "income_outcome".date, "income_outcome".outcome, "income_outcome".income
                         FROM "income_outcome"
                         INNER JOIN "user" ON "income_outcome".userId = "user".id
                         WHERE "user".email = '#$userEmail' AND "income_outcome".date BETWEEN '#$dateFrom' AND '#$dateTo'
                         GROUP BY "income_outcome".date
                      """
    }
    else if (dates.to.isDefined) {
      dateTo = Date.valueOf(dates.to.get.year + "-" + dates.to.get.month + "-" + dates.to.get.day).getTime
      dateFrom = Calendar.getInstance().getTimeInMillis

      sqlRequest = sql"""SELECT "income_outcome".date, "income_outcome".outcome, "income_outcome".income
                         FROM "income_outcome"
                         INNER JOIN "user" ON "income_outcome".userId = "user".id
                         WHERE "user".email = '#$userEmail' AND "income_outcome".date BETWEEN '#$dateFrom' AND '#$dateTo'
                         GROUP BY "income_outcome".date
                      """
    }
    else {
      // we don't use between and the dates
      sqlRequest = sql"""SELECT "income_outcome".date, "income_outcome".outcome, "income_outcome".income
                         FROM "income_outcome"
                         INNER JOIN "user" ON "income_outcome".userId = "user".id
                         WHERE "user".email = '#$userEmail'
                         GROUP BY "income_outcome".date
                      """
    }

    // too complicated request with slick directly
    dbConfig.db.run(sqlRequest.as[UsageGET]).map { usages =>
      val usagesToSend = usages.map { usage =>
        val dateToSend = DateDTO(usage.date.toString.substring(8, 10).toInt, usage.date.toString.substring(5, 7).toInt,
          usage.date.toString.substring(0, 4).toInt)

        val usageToReturn = UsageGETDTO(dateToSend, usage.used, usage.left)

        usageToReturn
      }

      usagesToSend
    }
  }
}
