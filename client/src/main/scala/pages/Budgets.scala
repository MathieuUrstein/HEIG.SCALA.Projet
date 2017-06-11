package pages

import org.scalajs.dom._

import scala.scalajs.js.annotation.JSExportTopLevel
import API.Models
import scala.scalajs.js
import scala.util.{Failure, Success}
import scala.concurrent._
import ExecutionContext.Implicits.global
import scala.scalajs.js.JSON

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
  var takesFrom: js.Array[Models.BudgetRef] = js.Array()
  var editedID: Int = 0

  private val budgetsWrapper = document.getElementById("budgets-wrapper").asInstanceOf[html.Div]
  private val takesFromWrapper = document.getElementById("takes-from-wrapper").asInstanceOf[html.Div]
  private val takesFromSelectorWrapper = document.getElementById("takes-from-selector-wrapper").asInstanceOf[html.Div]

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
    takesFromSelectorWrapper.innerHTML = ""

    API.getBudgets.onComplete {
      case Success(resp) =>
        val budgets = js.JSON.parse(resp.responseText).asInstanceOf[js.Array[Models.Budget]]
        budgets.sortBy(_.`type`).reverse.foreach(budget => {
          addBudget(budget)
          if (budget.`type` == "Income")
            addTakesFromSelector(budget)
        })
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
                  "<button onclick=\"budgetsSetEditedID(" + budget.id + ")\" class=\"btn edit\" data-toggle=\"modal\" data-target=\"#budgets-modal\"><i class=\"fa fa-ellipsis-v\" aria-hidden=\"true\"></i></button>" +
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

  def addTakesFromSelector(income: Models.Budget): Unit = {
    val a = "<a onclick=\"budgetsAddTakesFrom(" + income.id + ", '" + income.name + "')\" class=\"dropdown-item\" href=\"#\">" + income.name + "</a>"
    val div = document.createElement("div").asInstanceOf[html.Div]
    div.innerHTML = a
    takesFromSelectorWrapper.appendChild(div)
  }

  @JSExportTopLevel("budgetsAddTakesFrom")
  def addTakesFrom(id: Int, name: String): Unit = {
    if (takesFrom.indexWhere(_.budgetId == id) == -1) {
      takesFrom.append(new Models.BudgetRef(
        if (takesFrom.length == 0)
          0
        else
          takesFrom.last.order + 1,
        id
      ))
      val row = "<span class=\"order\">" + takesFrom.last.order + "</span> " + name + " <a onclick=\"budgetsRemoveTakesFrom(this.parentNode, " + id + ")\" href=\"#\" class=\"remove\"><i class=\"fa fa-times\" aria-hidden=\"true\"></i></a>"
      val div = document.createElement("div").asInstanceOf[html.Div]
      div.className = "related-income"
      div.innerHTML = row
      takesFromWrapper.appendChild(div)
    }
  }

  @JSExportTopLevel("budgetsRemoveTakesFrom")
  def removeTakesFrom(elem: html.Div, id: Int): Unit = {
    takesFromWrapper.removeChild(elem)
    takesFrom.remove(takesFrom.indexWhere(_.order == id))
  }

  @JSExportTopLevel("budgetsSetEditedID")
  def setEditedID(id: Int): Unit = {
    editedID = id

    id match {
      case 0 =>
        name = ""
        amount = 0.0

        document.getElementById("budget-name").asInstanceOf[html.Input].value = ""
        document.getElementById("budget-amount").asInstanceOf[html.Input].value = ""
      case _ =>
        API.getBudget(id).onComplete {
          case Success(resp) =>
            val budget = JSON.parse(resp.responseText).asInstanceOf[Models.Budget]
            `type` = budget.`type`
            name = budget.name
            amount = budget.used + budget.left + budget.exceeding
            persistent = budget.persistent
            reported = budget.reported
            color = budget.color
            takesFrom = budget.takesFrom

            document.getElementById("budget-name").asInstanceOf[html.Input].value = name
            document.getElementById("budget-amount").asInstanceOf[html.Input].value = amount.toString
            document.getElementById("persistent-selector").asInstanceOf[html.Link].innerHTML = persistent match {
              case 1 => "Every day"
              case 7  => "Every week"
              case 30 => "Every month"
              case 365 => "Every year"
              case _ => "None"
            }
            document.getElementById("reported-selector").asInstanceOf[html.Link].innerHTML = reported match {
              case true => "Yes"
              case _ => "No"
            }
            println(color)
            document.getElementById("color-selector").asInstanceOf[html.Link].innerHTML =
              "<span class=\"preview-color\" style=\"background: " + color + "\"></span> " + color match {
                case "aquamarine" => "Aquamarine"
                case "blueviolet" => "Blue violet"
                case "brown" => "Brown"
                case "cadetblue" => "CadetBlue"
                case "chartreuse" => "Chartreuse"
                case "chocolate" => "Chocolate"
                case "coral" => "Coral"
                case "cornflowerblue" => "Cornflower blue"
                case "darkblue" => "Dark blue"
                case "darkcyan" => "Dark cyan"
                case "darkgoldenrod" => "Dark golden rod"
                case "darkgreen" => "Dark green"
                case "darkkhaki" => "Dark khaki"
                case "darkmagenta" => "Dark magenta"
                case "darkorange" => "Dark orange"
                case "darksalmon" => "Dark salmon"
                case "darkturquoise" => "Dark turquoise"
                case "deeppink" => "Deep pink"
                case "deepskyblue" => "Deep sky blue"
                case "goldenrod" => "Golden rod"
                case "green" => "Green"
                case "indianred" => "Indian red"
                case "indigo" => "Indigo"
                case "lightslategray" => "Light slate gray"
                case "limegreen" => "Lime green"
                case "mediumblue" => "Medium blue"
                case "olive" => "Olive"
                case "orange" => "Orange"
                case other => other
              }
            takesFromWrapper.innerHTML = ""
            takesFrom.foreach(el => addTakesFrom(el.budgetId, "coco"))
            // TODO mettre a jour interface modale
          case Failure(e: ext.AjaxException) =>
            Utils.addAlert("danger", js.JSON.parse(e.xhr.responseText).selectDynamic("message").asInstanceOf[String])
        }
    }
  }

  @JSExportTopLevel("setPersistent")
  def setPersistent(elem: html.Link): Unit = {
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

    val budget = new Models.Budget(
      editedID,
      name,
      `type`,
      0,
      amount,
      0,
      persistent,
      reported,
      color,
      takesFrom
    )

    editedID match {
      case 0 =>
        API.postBudget(budget).onComplete {
          case Success(resp) =>
            Utils.addAlert("success", js.JSON.parse(resp.responseText).selectDynamic("message").asInstanceOf[String])
            list()
          case Failure(e: ext.AjaxException) =>
            Utils.addAlert("danger", js.JSON.parse(e.xhr.responseText).selectDynamic("message").asInstanceOf[String])
        }
      case _ =>
        API.patchBudget(budget).onComplete {
          case Success(resp) =>
            Utils.addAlert("success", js.JSON.parse(resp.responseText).selectDynamic("message").asInstanceOf[String])
            list()
          case Failure(e: ext.AjaxException) =>
            Utils.addAlert("danger", js.JSON.parse(e.xhr.responseText).selectDynamic("message").asInstanceOf[String])
        }
    }
  }
}
