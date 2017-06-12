package pages

import API.Models
import Facades._
import org.scalajs.dom._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.scalajs.js
import scala.util.{Failure, Success}


class Dashboard {
  Dashboard.list()
}

object Dashboard {

  private val defaultBudgetWrapper = document.getElementById("budgets-defaults-wrapper").asInstanceOf[html.Div]
  private val incomesWrapper = document.getElementById("incomes-wrapper").asInstanceOf[html.Div]
  private val outcomesWrapper = document.getElementById("outcomes-wrapper").asInstanceOf[html.Div]
  private val outcomeUsageBudgetsCanvas = document.getElementById("outcome-usage-budgets").asInstanceOf[html.Canvas]
  private val incomeUsageBudgetsCanvas = document.getElementById("income-left-budgets").asInstanceOf[html.Canvas]
  private val spendingDetailsCanvas = document.getElementById("spending-details-canvas").asInstanceOf[html.Canvas]
  private val inOutDetailsCanvas = document.getElementById("income-outcome-details-canvas").asInstanceOf[html.Canvas]

  private var spendingsChart: Chart = new Chart(spendingDetailsCanvas.getContext("2d").asInstanceOf[CanvasRenderingContext2D], Config())

  private def sortStringDate = (date1: String, date2: String) => {
    val day1 = date1.split("/")(0).toInt
    val month1 = date1.split("/")(1).toInt
    val day2 = date2.split("/")(0).toInt
    val month2 = date2.split("/")(1).toInt

    if (month1 < month2) true
    else if (month1 > month2) false
    else if (day1 < day2) true
    else false
  }

  def showIncomeOutcomeDetails(): Unit = {
    var incomeTotal = 0.0
    var outcomeTotal = 0.0

    API.getBudgets.onComplete {
      case Success(resp) =>
        val budgets = js.JSON.parse(resp.responseText).asInstanceOf[js.Array[Models.Budget]]
        budgets.foreach { budget =>
          if (budget.`type` == "Income") {
            incomeTotal += budget.left + budget.exceeding
          }
          else {
            outcomeTotal += budget.used + budget.exceeding
          }
        }
        var outcomePercent = 0

        if (incomeTotal + outcomeTotal == 0) {
          outcomePercent = 50
        } else if (outcomeTotal == 0) {
          outcomePercent = 0
        } else if(incomeTotal == 0) {
          outcomePercent = 100
        } else {
          outcomePercent = (outcomeTotal / (incomeTotal+outcomeTotal) * 100).toInt
        }
        document.getElementById("global-stats-resume").asInstanceOf[html.Div].innerHTML =
          "Outcome " + outcomePercent + "% - " + (100 - outcomePercent) + "% Income"
        document.getElementById("progress-outcome").asInstanceOf[html.Div].style.width = outcomePercent + "%"
        document.getElementById("progress-income").asInstanceOf[html.Div].style.width = (100 - outcomePercent) + "%"
        document.getElementById("progress-outcome").asInstanceOf[html.Div].innerHTML = outcomeTotal + " CHF"
        document.getElementById("progress-income").asInstanceOf[html.Div].innerHTML = incomeTotal + " CHF"
      case Failure(e: ext.AjaxException) =>
        Utils.addAlert("danger", js.JSON.parse(e.xhr.responseText).selectDynamic("message").asInstanceOf[String])
    }
  }

  def addBugetDoughnut(budgets: js.Array[Models.Budget], `type`: String): Unit = {
    var wrapper: html.Canvas = null
    val colors: js.Array[String] = js.Array()
    val lablels: js.Array[String] = js.Array()
    val data: js.Array[Double] = js.Array()

    if (`type` == "Income") wrapper = incomeUsageBudgetsCanvas
    else wrapper = outcomeUsageBudgetsCanvas

    budgets.foreach(budget => {
      colors.push(budget.color)
      lablels.push(budget.name)
      if (`type` == "Income") data.push(budget.left + budget.exceeding)
      else data.push(budget.used + budget.exceeding)
    })

    new Chart(
      wrapper.getContext("2d").asInstanceOf[CanvasRenderingContext2D],
      Config(
        `type` = "doughnut",
        data = Data(
          js.Array(
            Dataset(
              data = data,
              backgroundColor = colors
            )
          ),
          labels = lablels
        ),
        options = Options(
          legend = Legend(display = false)
        )
      )
    )
  }

  def showSpendingDetails(): Unit = {

    API.getSpenings.onComplete {
      case Success(resp) =>
        val spendings = js.JSON.parse(resp.responseText).asInstanceOf[js.Array[Models.Spending]]
        val labels: js.Array[String] = js.Array()
        val datasets: js.Array[Dataset] = js.Array()
        var budgetColor: Map[String, String] = Map.empty
        var dateBudgetValue: Map[String, Map[String, Double]] = Map.empty
        var budgetData: Map[String, js.Array[Double]] = Map.empty

        spendings.foreach(spending => {
          budgetColor += spending.budget -> spending.color
          val date = spending.date.day + "/" + spending.date.month
          if (dateBudgetValue.keySet.contains(date)) {
            var budgetValue: Map[String, Double] = dateBudgetValue(date)
            budgetValue += (spending.budget -> -spending.amount)
          } else {
            dateBudgetValue += date -> Map(spending.budget -> -spending.amount)
          }
        })

        budgetColor.keySet.foreach(budget => {
          budgetData += budget -> js.Array()
        })

        val orderedKeys = dateBudgetValue.keys.toList.sortWith(sortStringDate)
        orderedKeys.foreach(key => {
          labels.push(key)
          dateBudgetValue(key).foreach { case (budget, amount) =>
            budgetData(budget).push(amount)
          }
        })
        println("labels : " + labels.length)
        budgetColor.foreach { case(budget, color) =>
          println(budget + " : " + budgetData(budget).length)
          val colors: js.Array[String] = js.Array()
          labels.foreach(_ => colors.push(color))
          datasets.push(Dataset(
            label = budget,
            backgroundColor = colors,
            data = budgetData(budget)
          ))
        }

        spendingsChart = new Chart(
          spendingDetailsCanvas.getContext("2d").asInstanceOf[CanvasRenderingContext2D],
          Config(
            `type` = "bar",
            options = Options(
              tooltips = Tooltips(
                mode = "index",
                intersect = false
              ),
              scales = Scales(
                xAxes = js.Array(Stack(true)),
                yAxes = js.Array(Stack(true))
              )
            ),
            data = Data(
              labels = labels,
              datasets = datasets
            )
          )
        )
      case Failure(e: ext.AjaxException) =>
        Utils.addAlert("danger", js.JSON.parse(e.xhr.responseText).selectDynamic("message").asInstanceOf[String])
    }
  }

  def showInOutUsage(): Unit = {
    API.getUsage.onComplete {
      case Success(resp) =>
        val usages = js.JSON.parse(resp.responseText).asInstanceOf[js.Array[Models.Usage]]
        var dateInOut: Map[String, (Double, Double)] = Map.empty

        usages.foreach(usage => {
          dateInOut += (usage.date.day + "/" + usage.date.month) -> (usage.used, usage.left)
        })

        val labels: js.Array[String] = js.Array()
        val orderedKeys = dateInOut.keys.toList.sortWith(sortStringDate)
        val inData: js.Array[Double] = js.Array()
        val outData: js.Array[Double] = js.Array()
        orderedKeys.foreach(key => {
          labels.push(key)
          outData.push(dateInOut(key)._1)
          inData.push(dateInOut(key)._2)
        })

        val incomesDataSet: Dataset = Dataset(
          `type` = "line",
          label = "Income left",
          borderColor = "#c6527b",
          borderWidth = 2,
          fill = false,
          data = inData
        )
        val outcomesDataSet: Dataset = Dataset(
          `type` = "line",
          label = "Outcome usage",
          borderColor = "#f0ad4e",
          borderWidth = 2,
          fill = false,
          data = outData
        )

        new Chart(
          inOutDetailsCanvas.getContext("2d").asInstanceOf[CanvasRenderingContext2D],
          Config(
            `type` = "bar",
            options = Options(
              tooltips = Tooltips(mode = "index", intersect = false)
            ),
            data = Data(
              labels = labels,
              datasets = js.Array(
                incomesDataSet,
                outcomesDataSet
              )
            )
          )
        )
      case Failure(e: ext.AjaxException) =>
        Utils.addAlert("danger", js.JSON.parse(e.xhr.responseText).selectDynamic("message").asInstanceOf[String])
    }
  }

  def list(): Unit = {

    var incomesSum = 0.0
    var outcomesSum = 0.0

    incomesWrapper.innerHTML = ""
    outcomesWrapper.innerHTML = ""
    spendingsChart.destroy()

    showSpendingDetails()
    showInOutUsage()
    showIncomeOutcomeDetails()

    API.getBudgets.onComplete {
      case Success(resp) =>
        val budgets = js.JSON.parse(resp.responseText).asInstanceOf[js.Array[Models.Budget]]
        addBugetDoughnut(budgets.filter(_.`type` == "Income"), "Income")
        addBugetDoughnut(budgets.filter(_.`type` == "Outcome"), "Outcome")
        budgets.reverse.foreach(budget => {
          if (budget.`type` == "Income") incomesSum += budget.used + budget.left + budget.exceeding
          else outcomesSum += budget.used + budget.left + budget.exceeding
          addBudget(budget)
        })
        API.getExchanges.onComplete {
          case Success(respIn) =>
            val exchanges = js.JSON.parse(respIn.responseText).asInstanceOf[js.Array[Models.Exchange]]
            var borrowSum = 0.0
            var lendSum = 0.0
            exchanges.foreach(exchange =>
              exchange.`type` match {
                case "Borrow" =>
                  borrowSum += exchange.amount
                case _ =>
                  lendSum += exchange.amount
              })
            addDefaultBudgets(borrowSum, lendSum, incomesSum, outcomesSum, 0)
          case Failure(e: ext.AjaxException) =>
            Utils.addAlert("danger", js.JSON.parse(e.xhr.responseText).selectDynamic("message").asInstanceOf[String])
        }
      case Failure(e: ext.AjaxException) =>
        Utils.addAlert("danger", js.JSON.parse(e.xhr.responseText).selectDynamic("message").asInstanceOf[String])
    }
  }

  def addBudget(budget: Models.Budget): Unit = {

    var usedPercent = 0
    var leftPercent = 0
    var exceedingPercent = 0

    if (budget.used == 0 && budget.left == 0) {
      usedPercent = 50
      leftPercent = 50
    } else {
      leftPercent = (budget.left / (budget.left + budget.used + budget.exceeding) * 100).toInt
      usedPercent = (budget.used / (budget.left + budget.used + budget.exceeding) * 100).toInt
      exceedingPercent = (budget.exceeding / (budget.left + budget.used + budget.exceeding) * 100).toInt

      if (budget.`type` == "Income")
        leftPercent = 100 - exceedingPercent - usedPercent
      else
        usedPercent = 100 - exceedingPercent - leftPercent
    }

    val row =   "<h6>" +
      budget.name +
      "<span class=\"float-right\">" +
      (if (budget.reported)
        "<i class=\"fa fa-retweet\" aria-hidden=\"true\"></i>" else "") +
      (if (budget.persistent != 0)
        "<i class=\"fa fa-calendar-check-o\" aria-hidden=\"true\"></i>" else "") +
      (if (budget.`type` == "Income")
        "<i class=\"fa fa-arrow-down\" aria-hidden=\"true\"></i>"
      else
        "<i class=\"fa fa-arrow-up\" aria-hidden=\"true\"></i>") +
      "</span>" +
      "</h6>" +
      "<div class=\"progress\">" +
      (if (budget.`type` == "Income")
        "<div class=\"progress-bar\" role=\"progressbar\" style=\"width: " + exceedingPercent + "%; background: green\">" + budget.exceeding + " CHF</div>" +
          "<div class=\"progress-bar bg-faded left\" role=\"progressbar\" style=\"width: " + usedPercent + "%;\">" + budget.used + " CHF</div>" +
          "<div class=\"progress-bar\" role=\"progressbar\" style=\"width: " + leftPercent + "%; background: " + budget.color + "\">" + budget.left + " CHF</div>"
      else
        "<div class=\"progress-bar\" role=\"progressbar\" style=\"width: " + usedPercent + "%; background: " + budget.color + "\">" + budget.used + " CHF</div>" +
          "<div class=\"progress-bar bg-faded left\" role=\"progressbar\" style=\"width: " + leftPercent + "%;\">" + budget.left + " CHF</div>" +
          "<div class=\"progress-bar\" role=\"progressbar\" style=\"width: " + exceedingPercent + "%; background: red\">" + budget.exceeding + " CHF</div>") +
      "</div>" +
      "<div class=\"details row\">" +
      "<div class=\"col-3 text-left\">" +
      "| 0 CHF" +
      "</div>" +
      "<div class=\"col-6 text-center\">" +
      (if (budget.`type` == "Income")
        "Used " + usedPercent + "% - " + (leftPercent + 2*exceedingPercent) + "% Left"
      else
        "Used " + (usedPercent + 2*exceedingPercent) + "% - " + leftPercent + "% Left") +
      "</div>" +
      "<div class=\"col-3 text-right\">" +
      (budget.used + budget.left + budget.exceeding) + " CHF |" +
      "</div>" +
      "</div>"

    val div = document.createElement("div").asInstanceOf[html.Div]
    div.className = "budget"
    div.innerHTML = row

    if (budget.`type` == "Income")
      incomesWrapper.appendChild(div)
    else
      outcomesWrapper.appendChild(div)
  }

  def addDefaultBudgets(borrowSum: Double, lendSum: Double, incomeSum: Double, outcomeSum: Double, debtSum: Double): Unit = {
    val borrowPercent: Int = (borrowSum / (borrowSum + lendSum) * 100).toInt
    val exchanges = "<div class=\"cat full-budget\">" +
      "<h4>" +
      "Exchanges" +
      "<span class=\"float-right\">" +
      "<i class=\"fa fa-refresh\" aria-hidden=\"true\"></i>" +
      "</span>" +
      "</h4>" +
      "<div class=\"progress\">" +
      "<div class=\"progress-bar bg-danger\" role=\"progressbar\" style=\"width: " + borrowPercent + "%;\">" + borrowSum + " CHF</div>" +
      "<div class=\"progress-bar bg-success\" role=\"progressbar\" style=\"width: " + (100 - borrowPercent) + "%;\">" + lendSum + " CHF</div>" +
      "</div>" +
      "<div class=\"details row\">" +
      "<div class=\"col-3 text-left\">" +
      "| 0 CHF" +
      "</div>" +
      "<div class=\"col-6 text-center\">" +
      "Borrow " + borrowPercent + "% - " + (100 - borrowPercent) + "% Lend" +
      "</div>" +
      "<div class=\"col-3 text-right\">" +
      (borrowSum + lendSum) + " CHF |" +
      "</div>" +
      "</div>" +
      "</div>"

    val outcomePercent: Int = (outcomeSum / (incomeSum + outcomeSum) * 100).toInt
    val balance = "<div class=\"cat full-budget\">" +
      "<h4>" +
      "Balance" +
      "<span class=\"float-right\">" +
      "<i class=\"fa fa-balance-scale\" aria-hidden=\"true\"></i>" +
      "</span>" +
      "</h4>" +
      "<div class=\"progress\">" +
      "<div class=\"progress-bar bg-danger\" role=\"progressbar\" style=\"width: " + outcomePercent + "%;\">" + outcomeSum + " CHF</div>" +
      "<div class=\"progress-bar bg-success\" role=\"progressbar\" style=\"width: " + (100 -outcomePercent) + "%;\">" + incomeSum + " CHF</div>" +
      "</div>" +
      "<div class=\"details row\">" +
      "<div class=\"col-3 text-left\">" +
      "| 0 CHF" +
      "</div>" +
      "<div class=\"col-6 text-center\">" +
      "Outcome " + outcomePercent + "% - " + (100 -outcomePercent) + "% Income" +
      "</div>" +
      "<div class=\"col-3 text-right\">" +
      (outcomeSum + incomeSum) +" CHF |" +
      "</div>" +
      "</div>" +
      "</div>"

    val debt =  "<div class=\"cat full-budget\">" +
      "<h4>" +
      "Debt" +
      "<span class=\"float-right\">" +
      "<i class=\"fa fa-asterisk\" aria-hidden=\"true\"></i>" +
      "</span>" +
      "</h4>" +
      "<div class=\"progress\">" +
      "<div class=\"progress-bar bg-debt\" role=\"progressbar\" style=\"width: 100%;\">" + debtSum + " CHF</div>" +
      "</div>" +
      "<div class=\"details row\">" +
      "<div class=\"col-3 text-left\">" +
      "| 0 CHF" +
      "</div>" +
      "<div class=\"col-6 text-center\">" +
      "When all incomes are used, the outcomes uses debt" +
      "</div>" +
      "<div class=\"col-3 text-right\">" +
      debtSum + " CHF |" +
      "</div>" +
      "</div>" +
      "</div>"

    defaultBudgetWrapper.innerHTML = exchanges + balance + debt
  }
}