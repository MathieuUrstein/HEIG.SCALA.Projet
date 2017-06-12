package API

import scala.scalajs._
import scala.scalajs.js.annotation.ScalaJSDefined

package object Models {
  @ScalaJSDefined
  class BudgetRef(
                   val order: Int,
                   val budgetId: Int
                 ) extends js.Object

  @ScalaJSDefined
  class Date(
              val day: Int,
              val month: Int,
              val year: Int
            ) extends js.Object

  @ScalaJSDefined
  class User(
              val email: String,
              val fullname: String,
              val password: String,
              val currency: String
            ) extends js.Object

  @ScalaJSDefined
  class UserPublic(val email: String,
                   val fullname: String,
                   val currency: String
                  ) extends js.Object

  @ScalaJSDefined
  class Credentials(val email: String,
                    val password: String
                   ) extends js.Object

  @ScalaJSDefined
  class Transaction(val id: Int,
                    val name: String,
                    val date: Date,
                    val budgetId: Int,
                    val amount: Double
                      ) extends js.Object

  @ScalaJSDefined
  class TransactionBudget(val id: Int,
                          val name: String
                         ) extends js.Object

  @ScalaJSDefined
  class TransactionGET(val id: Int,
                       val name: String,
                       val date: Date,
                       val budget: TransactionBudget,
                       val amount: Double
                   ) extends js.Object

  @ScalaJSDefined
  class Exchange(val id: Int,
                 val name: String,
                 val date: Date,
                 val `type`: String,
                 val amount: Double
                ) extends js.Object

  @ScalaJSDefined
  class Budget(
                val id: Int,
                val name: String,
                val `type`: String,
                val used: Double,
                val left: Double,
                val exceeding: Double,
                val persistent: Int,
                val reported: Boolean,
                val color: String,
                val takesFrom: js.Array[BudgetRef]
              ) extends js.Object

  @ScalaJSDefined
  class Spending(
                  val date: Date,
                  val budget: String,
                  val amount: Double,
                  val color: String
                ) extends js.Object

  @ScalaJSDefined
  class Usage(
               val date: Date,
               val used: Double,
               val left: Double
             ) extends js.Object

  @ScalaJSDefined
  class DateRange(
                   val from: Date,
                   val to: Date
                 ) extends js.Object
}
