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

      sqlRequest = sql"""SELECT "transaction".date, "budget".name, SUM("transaction".amount)
                         FROM "budget"
                         INNER JOIN "transaction" ON "budget".id = "transaction".budgetId
                         INNER JOIN "user" ON "transaction".userId = "user".id
                         WHERE "user".email = '#$userEmail' AND "budget".type = 'outcome'
                         AND 'transaction'.date BETWEEN '#$dateFrom' AND '#$dateTo'
                         GROUP BY "transaction".date, "budget".id
                      """
    }
    else if (dates.to.isDefined) {
      dateTo = Date.valueOf(dates.to.get.year + "-" + dates.to.get.month + "-" + dates.to.get.day).getTime
      dateFrom = Calendar.getInstance().getTimeInMillis

      sqlRequest = sql"""SELECT "transaction".date, "budget".name, SUM("transaction".amount)
                         FROM "budget"
                         INNER JOIN "transaction" ON "budget".id = "transaction".budgetId
                         INNER JOIN "user" ON "transaction".userId = "user".id
                         WHERE "user".email = '#$userEmail' AND "budget".type = 'outcome'
                         AND 'transaction'.date BETWEEN '#$dateFrom' AND '#$dateTo'
                         GROUP BY "transaction".date, "budget".id
                      """
    }
    else {
      // we don't use between and the dates
      sqlRequest = sql"""SELECT "transaction".date, "budget".name, SUM("transaction".amount)
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

        val spendingToReturn = SpendingGETDTO(dateToSend, spending.budget, spending.amount)

        spendingToReturn
      }

      spendingsToSend
    }
  }

  /*def findUsage(userEmail: String, dates: FromToDatesDTO): Future[Vector[SpendingGETDTO]] = {
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

      sqlRequest = sql"""SELECT "transaction".date, "budget".name, SUM("transaction".amount)
                         FROM "budget"
                         INNER JOIN "transaction" ON "budget".id = "transaction".budgetId
                         INNER JOIN "user" ON "transaction".userId = "user".id
                         WHERE "user".email = '#$userEmail' AND "budget".type = 'outcome'
                         AND 'transaction'.date BETWEEN '#$dateFrom' AND '#$dateTo'
                         GROUP BY "transaction".date, "budget".id
                      """
    }
    else if (dates.to.isDefined) {
      dateTo = Date.valueOf(dates.to.get.year + "-" + dates.to.get.month + "-" + dates.to.get.day).getTime
      dateFrom = Calendar.getInstance().getTimeInMillis

      sqlRequest = sql"""SELECT "transaction".date, "budget".name, SUM("transaction".amount)
                         FROM "budget"
                         INNER JOIN "transaction" ON "budget".id = "transaction".budgetId
                         INNER JOIN "user" ON "transaction".userId = "user".id
                         WHERE "user".email = '#$userEmail' AND "budget".type = 'outcome'
                         AND 'transaction'.date BETWEEN '#$dateFrom' AND '#$dateTo'
                         GROUP BY "transaction".date, "budget".id
                      """
    }
    else {
      // we don't use between and the dates
      sqlRequest = sql"""SELECT "transaction".date, "budget".name, SUM("transaction".amount)
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

        val spendingToReturn = SpendingGETDTO(dateToSend, spending.budget, spending.amount)

        spendingToReturn
      }

      spendingsToSend
    }
  }*/
}
