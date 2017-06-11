package scheduling

import java.sql.Date
import java.util.Calendar
import javax.inject.{Inject, Singleton}

import akka.actor.Actor
import dao.{BudgetDAO, IncomeOutcomeDAO, TransactionDAO, UserDAO}
import models.IncomeOutcome
import utils.Const

import scala.collection.immutable.ListMap
import scala.concurrent.{Await, ExecutionContext}
import scala.concurrent.duration.Duration

@Singleton
class SchedulerActor @Inject()(userDAO: UserDAO, budgetDAO: BudgetDAO, transactionDAO: TransactionDAO,
                               incomeOutcomeDAO: IncomeOutcomeDAO)(implicit ec: ExecutionContext) extends Actor {
  override def receive: Receive = {
    case _ =>
      println("Job is running")

      // we add new entries for income_outcome table for all users
      userDAO.getAllEmails.map { allUsersEmails =>
        allUsersEmails.foreach { email =>
          Await.ready(budgetDAO.findAll(email).map { budgets =>
            var totalIncomeLeft: Double = 0
            var totalOutcomeUsed: Double = 0

            budgets.foreach { budget =>
              if (budget.`type` == "Income") {
                totalIncomeLeft += budget.left + budget.exceeding
              }
              else {
                totalOutcomeUsed += budget.used + budget.exceeding
              }
            }

            Await.ready(userDAO.getId(email).map { userId =>
              Await.ready(incomeOutcomeDAO.insert(IncomeOutcome(Date.valueOf(Const.format.format(Calendar.getInstance().getTime)),
                totalIncomeLeft, totalOutcomeUsed, userId)), Duration(Const.maxTimeToWaitInSeconds, Const.timeToWaitUnit))
            }, Duration(Const.maxTimeToWaitInSeconds, Const.timeToWaitUnit))
          }, Duration(Const.maxTimeToWaitInSeconds, Const.timeToWaitUnit))
        }
      }

      // we check if we must update a budget (reinitialize it)
      budgetDAO.getAllBudgets.map { budgets =>
        budgets.foreach { budget =>
          // to know if we must consider the actual exceeding and report it to the new budget
          val exceedingToReport = budget.reported
          val creationDate = budget.creationDate
          val delayInDays = budget.persistent
          val calendar = Calendar.getInstance()
          val today = Calendar.getInstance()

          calendar.setTime(creationDate)
          calendar.add(Calendar.DAY_OF_MONTH, delayInDays)
          today.setTime(Date.valueOf(Const.format.format(Calendar.getInstance().getTime)))

          if (calendar.compareTo(today) == 0) {
            // we have to make the update
            val initialLeftValue = budget.used + budget.left

            if (!exceedingToReport) {
              // we don't need to consider the actual exceeding
              // we just need to reinitialize used, left and exceeding values
              Await.ready(budgetDAO.reinitializeBudget(budget.id, 0, initialLeftValue, 0),
                Duration(Const.maxTimeToWaitInSeconds, Const.timeToWaitUnit))
            }
            else {
              val exceeding = budget.exceeding

              if (exceeding == 0)  {
                // we don't have exceeding to report
                // we just need to reinitialize used and left
                Await.ready(budgetDAO.reinitializeBudget(budget.id, 0, initialLeftValue, 0),
                  Duration(Const.maxTimeToWaitInSeconds, Const.timeToWaitUnit))
              }
              else {
                // we have to know if the exceeding is positive or negative (Income or Outcome)
                // if it is a positive exceeding, we do nothing
                if (budget.`type` == "Outcome") {
                  // firstly, we reinitialize the budget
                  Await.ready(budgetDAO.reinitializeBudget(budget.id, 0, initialLeftValue, 0),
                    Duration(Const.maxTimeToWaitInSeconds, Const.timeToWaitUnit))
                  // we update the new budget with the exceeding
                  Await.ready(transactionDAO.updateBudgetOutcome(budget.id, -exceeding),
                    Duration(Const.maxTimeToWaitInSeconds, Const.timeToWaitUnit))

                  var budgetsMap: Map[Int, Int] = Map()

                  // we look for the order to treat the Income budgets
                  Await.ready(budgetDAO.getTakesFrom(budget.id).map { budgets =>
                    budgets.foreach { b =>
                      budgetsMap += b.order -> b.budgetId
                    }
                  }, Duration(Const.maxTimeToWaitInSeconds, Const.timeToWaitUnit))

                  val sortedMap = ListMap(budgetsMap.toSeq.sortWith(_._1 < _._1):_*)
                  var returnedExceeding: Double = -exceeding

                  // we stop when we have a null exceeding or all Income budgets have been processed
                  sortedMap.takeWhile(_ => returnedExceeding != 0).foreach { budgetTakesFrom =>
                    // updates takesFrom budgets (Income) in order
                    // Income budgets are considered as outcome
                    returnedExceeding = transactionDAO.updateBudgetIncomeAsOutcome(budgetTakesFrom._2, returnedExceeding)
                  }
                }
              }
            }
          }
        }
      }
  }
}
