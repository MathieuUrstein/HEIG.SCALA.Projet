package API


package object Models {
  class Date(day: Int, month: Int, year: Int)

  class User(email: String, fullname: String, password: String, currency: String)
  class UserPublic(email: String, fullname: String, currency: String)

  class Credentials(email: String, password: String)

  class TransactionBudget(name: String, date: Date, budgetId: Int, amount: Double)
  class Transaction(name: String, date: Date, budget: TransactionBudget, amount: Double)

  class Exchange(name: String, date: Date, `type`: String, amount: Double)

  private class BudgetRef(order: Int, budgetId: Int)
  class Budget(
    name: String,
    `type`: String,
    used: Double,
    left: Double,
    exceeding: Double,
    persistant: Int,
    reported: Boolean,
    color: String,
    takesFrom: List[BudgetRef]
  )

  class Spending(date: Date, budget: String, amount: Double)

  class Usage(date: Date, used: Double, left: Double)
}
