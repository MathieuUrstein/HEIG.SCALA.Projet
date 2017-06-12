package models

import java.sql.Date

import slick.jdbc.GetResult

case class User(fullname: String, email: String, var password: String, currency: String)
case class UserGETDTO(fullname: String, email: String, currency: String)
case class UserPATCHDTO(fullname: Option[String], email: Option[String], var password: Option[String],
                        currency: Option[String])
case class LoginFormDTO(email: String, password: String)

// TODO: delete the error with a 0 in front of a number (improvement)

case class DateDTO(day: Int, month: Int, year: Int)
case class FromToDatesDTO(from: Option[DateDTO], to: Option[DateDTO])

case class Transaction(name: String, date: Date, amount: Double, userId: Int, budgetId: Int)
case class TransactionPOSTDTO(name: String, date: Option[DateDTO], budgetId: Int, amount: Double)
case class TransactionGET(id: Int, name: String, date: Date, budgetId: Int, amount: Double)
case class TransactionAllGETDTO(id: Int, name: String, date: DateDTO, transaction: TransactionBudgetGETDTO,
                                amount: Double)
case class TransactionGETDTO(name: String, date: DateDTO, transaction: TransactionBudgetGETDTO, amount: Double)
case class TransactionPATCHDTO(name: Option[String], date: Option[DateDTO], budgetId: Option[Int], amount: Option[Double])
case class TransactionBudgetGETDTO(id: Int, name: String)

case class Exchange(name: String, date: Date, `type`: String, amount: Double, userId: Int)
case class ExchangePOSTDTO(name: String, date: Option[DateDTO], `type`: String, amount: Double)
case class ExchangeGET(id: Int, name: String, date: Date, `type`: String, amount: Double)
case class ExchangeAllGETDTO(id: Int, name: String, date: DateDTO, `type`: String, amount: Double)
case class ExchangeGETDTO(name: String, date: DateDTO, `type`: String, amount: Double)
case class ExchangePATCHDTO(name: Option[String], date: Option[DateDTO], `type`: Option[String], amount: Option[Double])

case class Budget(name: String, creationDate: Date, `type`: String, used: Double, left: Double, exceeding: Double, persistent: Int,
                  reported: Boolean, color: String, userId: Int)
case class TakesFrom(order: Int, budgetGoesToId: Int, budgetTakesFromId: Int)
case class TakesFromDTO(order: Int, budgetId: Int)
case class BudgetPOSTDTO(name: String, `type`: String, used: Double, left: Double, exceeding: Double, persistent: Int,
                         reported: Boolean, color: String, takesFrom: Seq[TakesFromDTO])
case class BudgetGET(id: Int, creationDate: Date, name: String, `type`: String, used: Double, left: Double, exceeding: Double,
                     persistent: Int, reported: Boolean, color: String)
case class BudgetAndTakesFromAllGETDTO(id: Int, name: String, `type`: String, used: Double, left: Double,
                                       exceeding: Double, persistent: Int, reported: Boolean, color: String,
                                       takesFrom: Option[Seq[TakesFromDTO]])
case class BudgetAndTakesFromGETDTO(name: String, `type`: String, used: Double, left: Double, exceeding: Double,
                                    persistent: Int, reported: Boolean, color: String,
                                    takesFrom: Option[Seq[TakesFromDTO]])
case class BudgetPATCHDTO(name: Option[String], used: Option[Double], left: Option[Double], exceeding: Option[Double],
                          persistent: Option[Int], reported: Option[Boolean], color: Option[String],
                          takesFrom: Option[Seq[TakesFromDTO]])

case class SpendingGETDTO(date: DateDTO, budget: String, amount: Double, color: String)
case class SpendingGET(date: Date, budget: String, amount: Double, color: String)
case class UsageGETDTO(date: DateDTO, used: Double, left: Double)
case class UsageGET(date: Date, used: Double, left: Double)

object SpendingGET {
  implicit val spendingGETResult = GetResult(r => SpendingGET(r.nextDate, r.nextString, r.nextDouble, r.nextString))
}
object UsageGET {
  implicit val usageGETResult = GetResult(r => UsageGET(r.nextDate, r.nextDouble, r.nextDouble))
}

case class IncomeOutcome(date: Date, income: Double, outcome: Double, userId: Int)