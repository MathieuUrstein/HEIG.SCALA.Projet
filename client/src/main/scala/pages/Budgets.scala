package pages

import org.scalajs.dom._

import scala.scalajs.js.annotation.JSExportTopLevel
import API.Models
import scala.scalajs.js
import scala.scalajs.js.JSON
import scala.util.{Failure, Success}
import scala.concurrent._
import ExecutionContext.Implicits.global

class Budgets {
  Budgets.list()
}

object Budgets {
  var `type`: String = "Income"
  var name: String = ""
  var amount: Double = 0.0
  var persistent: Int = 0
  var reported: Boolean = false
  var color: String = "chocolate"
  var takesFrom: List[Int] = List.empty
  var editedID: Int = 0

  private val budgetsWrapper = document.getElementById("budgets-wrapper").asInstanceOf[html.Div]

  private def getValues(): Unit = {
    name = document.getElementById("budget-name").asInstanceOf[html.Input].value
    amount = document.getElementById("budget-amount").asInstanceOf[html.Input].value.toDouble
    if (
      document.getElementById("income-tab-link").asInstanceOf[html.Div].className.contains("active")
    ) `type` = "Income"
    else `type` = "Outcome"
  }

  def list(): Unit = {
    var incomeSum = 0.0
    var outcomeSum = 0.0

    budgetsWrapper.innerHTML = ""

    API.getBudgets.onComplete {
      case Success(resp) =>
        val budgets = js.JSON.parse(resp.responseText).asInstanceOf[js.Array[Models.Budget]]
        budgets.sortBy(_.`type`).reverse.foreach(budget =>
          addBudget(budget)
        )
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

    val row =   "<h4>" +
                  "<button class=\"btn edit\" data-toggle=\"modal\" data-target=\"#budgets-modal\"><i class=\"fa fa-ellipsis-v\" aria-hidden=\"true\"></i></button>" +
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
                "</h4>" +
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
                    "Used " + usedPercent + "% - " + leftPercent + "% Left" +
                  "</div>" +
                  "<div class=\"col-3 text-right\">" +
                    (budget.used + budget.left + budget.exceeding) + " CHF |" +
                  "</div>" +
                "</div>"

    val div = document.createElement("div").asInstanceOf[html.Div]
    div.className = "budget"
    div.innerHTML = row
    budgetsWrapper.appendChild(div)
  }

  @JSExportTopLevel("budgetsSetEditedID")
  def setEditedID(id: Int): Unit = {
    editedID = id
  }

  @JSExportTopLevel("setPersistant")
  def setPersistant(elem: html.Link): Unit = {
    persistent = elem.innerHTML match {
      case "Every day" => 1
      case "Every week" => 7
      case "Every month" => 30
      case "Every year" => 365
      case _ => 0
    }
    document.getElementById("persistent-selector").innerHTML = elem.innerHTML
  }
  @JSExportTopLevel("setReported")
  def setReported(elem: html.Link): Unit = {
    reported = elem.innerHTML match {
      case "Yes" => true
      case _ => false
    }
    document.getElementById("reported-selector").innerHTML = elem.innerHTML
  }
  @JSExportTopLevel("setColor")
  def setColor(elem: html.Link): Unit = {
    val pattern = "(<span.*background: )(.*)(\"><.*)".r
    val pattern(_, colorName, _) = elem.innerHTML
    color = colorName
    document.getElementById("color-selector").innerHTML = elem.innerHTML
  }

  @JSExportTopLevel("budgetsDelete")
  def delete(): Unit = {
    if(editedID != 0) {
      API.deleteBudget(editedID).onComplete {
        case Success(resp) =>
          Utils.addAlert("success", js.JSON.parse(resp.responseText).selectDynamic("message").asInstanceOf[String])
          list()
        case Failure(e: ext.AjaxException) =>
          Utils.addAlert("danger", js.JSON.parse(e.xhr.responseText).selectDynamic("message").asInstanceOf[String])
      }
    }
  }

  @JSExportTopLevel("budgetsSave")
  def save(): Unit = {
    getValues()

    editedID match {
      case 0 =>
        val budget = new Models.Budget(
          0,
          name,
          `type`,
          0,
          amount,
          0,
          persistent,
          reported,
          color,
          js.Array()
        )
        println(JSON.stringify(budget))

        API.postBudget(budget).onComplete {
          case Success(resp) =>
            Utils.addAlert("success", js.JSON.parse(resp.responseText).selectDynamic("message").asInstanceOf[String])
            list()
          case Failure(e: ext.AjaxException) =>
            Utils.addAlert("danger", js.JSON.parse(e.xhr.responseText).selectDynamic("message").asInstanceOf[String])
        }
      case _ =>
    }
  }
}
