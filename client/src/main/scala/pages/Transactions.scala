package pages

import API.Models
import org.scalajs.dom.{document, ext, html}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.scalajs.js
import scala.scalajs.js.JSON
import scala.scalajs.js.annotation.JSExportTopLevel
import scala.util.{Failure, Success}

class Transactions {
  Transactions.list()
}

object Transactions {
  var name: String = ""
  var date: Models.Date = Utils.now
  var budgetID: Int = 0
  var budgetUpdateID: Int = 0
  var budgetName: String = ""
  var amount: Double = 0.0
  var editedID: Int = 0

  private val transactionWrapper = document.getElementById("transaction-wrapper").asInstanceOf[html.Table]
  private val existingBudgets = document.getElementById("transaction-budgets").asInstanceOf[html.Div]
  private val existingBudgetsModal = document.getElementById("modal-transaction-budgets").asInstanceOf[html.Div]

  private def getCreationValues(): Unit = {
    name = document.getElementById("transaction-name").asInstanceOf[html.Input].value
    amount = document.getElementById("transaction-amount").asInstanceOf[html.Input].value.toDouble
  }

  private def getUpdateValues(): Unit = {
    name = document.getElementById("modal-transaction-name").asInstanceOf[html.Input].value
    amount = document.getElementById("modal-transaction-amount").asInstanceOf[html.Input].value.toDouble
  }

  def list(): Unit = {
    var incomeTotal = 0.0
    var outcomeTotal = 0.0

    transactionWrapper.innerHTML = ""
    existingBudgets.innerHTML = ""

    API.getBudgets.onComplete {
      case Success(resp) =>
        val budgets = js.JSON.parse(resp.responseText).asInstanceOf[js.Array[Models.Budget]]
        budgets.foreach { budget =>
          addExistingBudget(budget)
        }

        API.getTransactions.onComplete {
          case Success(response) =>
            val transactions = js.JSON.parse(response.responseText).asInstanceOf[js.Array[Models.TransactionGET]]
            transactions.foreach { transaction =>
              if (transaction.amount > 0) {
                incomeTotal += transaction.amount
              }
              else {
                outcomeTotal += transaction.amount
              }

              addTransaction(transaction)
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

            val divToEnd = "<div class=\"dropdown-divider\"></div>"
            val linkToEnd = "<a class=\"dropdown-item\" href=\"/budgets\"><i class=\"fa fa-plus\" aria-hidden=\"true\"></i> Add budget</a>"
            val div = document.createElement("div").asInstanceOf[html.Div]
            val a = document.createElement("a").asInstanceOf[html.Link]

            div.innerHTML = divToEnd
            a.innerHTML = linkToEnd
            existingBudgets.appendChild(div)
            existingBudgets.appendChild(a)
          case Failure(e: ext.AjaxException) =>
            Utils.addAlert("danger", js.JSON.parse(e.xhr.responseText).selectDynamic("message").asInstanceOf[String])
        }
      case Failure(e: ext.AjaxException) =>
        Utils.addAlert("danger", js.JSON.parse(e.xhr.responseText).selectDynamic("message").asInstanceOf[String])
    }
  }

  def addExistingBudget(budget: Models.Budget): Unit = {
    val link = "<a onClick=\"setBudget(this)\" id=" + budget.id + " class=\"dropdown-item\" href=\"#\">" + budget.name + "</a>"
    val a = document.createElement("a").asInstanceOf[html.Link]
    a.innerHTML = link
    existingBudgets.appendChild(a)
  }

  def addModalExistingBudget(budget: Models.Budget): Unit = {
    val link = "<a onClick=\"setModalBudget(this)\" id=" + budget.id + " class=\"dropdown-item\" href=\"#\">" + budget.name + "</a>"
    val a = document.createElement("a").asInstanceOf[html.Link]
    a.innerHTML = link
    existingBudgetsModal.appendChild(a)
  }

  def addTransaction(transaction: Models.TransactionGET): Unit = {
    val row =   "<td width=\"3%\" class=\"edit\"><button onclick=\"transactionsSetEditedID(" + transaction.id + ")\" class=\"btn\" data-toggle=\"modal\" data-target=\"#transactions-modal\"><i class=\"fa fa-ellipsis-v\" aria-hidden=\"true\"></i></button></td>" +
      "<td width=\"47%\" class=\"name\">" + transaction.name + " <span class=\"date\">" + Utils.prettyDate(transaction.date) + "</span></td>" +
      "<td width=\"30%\"><a href=\"/budgets\">"+ transaction.budget.name +"</a></td>" +
      "<td width=\"20%\" class=\"amount\">"+ transaction.amount +" CHF</td>"
    val tr = document.createElement("tr").asInstanceOf[html.TableRow]
    tr.innerHTML = row
    transactionWrapper.appendChild(tr)
  }

  @JSExportTopLevel("setBudget")
  def setBudget(elem: html.Link): Unit = {
    budgetID = elem.id.toInt
    document.getElementById("transaction-budget-selector").innerHTML = elem.innerHTML
  }

  @JSExportTopLevel("setModalBudget")
  def setModalBudget(elem: html.Link): Unit = {
    budgetUpdateID = elem.id.toInt
    document.getElementById("modal-transaction-budget-selector").innerHTML = elem.innerHTML
  }

  @JSExportTopLevel("transactionsSetEditedID")
  def setEditedID(id: Int): Unit = {
    editedID = id

    API.getTransaction(id).onComplete {
      case Success(resp) =>
        existingBudgetsModal.innerHTML = ""
        API.getBudgets.onComplete {
          case Success(response) =>
            val budgets = js.JSON.parse(response.responseText).asInstanceOf[js.Array[Models.Budget]]
            budgets.foreach { budget =>
              addModalExistingBudget(budget)
            }

            val divToEnd = "<div class=\"dropdown-divider\"></div>"
            val linkToEnd = "<a class=\"dropdown-item\" href=\"/budgets\"><i class=\"fa fa-plus\" aria-hidden=\"true\"></i> Add budget</a>"
            val div = document.createElement("div").asInstanceOf[html.Div]
            val a = document.createElement("a").asInstanceOf[html.Link]

            div.innerHTML = divToEnd
            a.innerHTML = linkToEnd
            existingBudgetsModal.appendChild(div)
            existingBudgetsModal.appendChild(a)

            val transaction = JSON.parse(resp.responseText).asInstanceOf[Models.TransactionGET]
            name = transaction.name
            date = transaction.date
            budgetUpdateID = transaction.budget.id
            budgetName = transaction.budget.name
            amount = transaction.amount

            document.getElementById("modal-transaction-name").asInstanceOf[html.Input].value = name
            document.getElementById("modal-transaction-budget-selector").innerHTML = budgetName
            document.getElementById("modal-transaction-amount").asInstanceOf[html.Input].value = amount.toString
          case Failure(e: ext.AjaxException) =>
            Utils.addAlert("danger", js.JSON.parse(e.xhr.responseText).selectDynamic("message").asInstanceOf[String])
        }
      case Failure(e: ext.AjaxException) =>
        Utils.addAlert("danger", js.JSON.parse(e.xhr.responseText).selectDynamic("message").asInstanceOf[String])
    }
  }

  @JSExportTopLevel("transactionsDelete")
  def delete(): Unit = {
    if (editedID != 0) {
      API.deleteTransaction(editedID).onComplete {
        case Success(resp) =>
          Utils.addAlert("success", js.JSON.parse(resp.responseText).selectDynamic("message").asInstanceOf[String])
          list()
        case Failure(e: ext.AjaxException) =>
          Utils.addAlert("danger", js.JSON.parse(e.xhr.responseText).selectDynamic("message").asInstanceOf[String])
      }
    }
  }

  @JSExportTopLevel("transactionsCreate")
  def create(): Unit = {
    if (budgetID == 0) {
      return
    }

    getCreationValues()

    val transaction = new Models.Transaction(
      editedID,
      name,
      date,
      budgetID,
      amount
    )

    API.postTransaction(transaction).onComplete {
      case Success(resp) =>
        Utils.addAlert("success", js.JSON.parse(resp.responseText).selectDynamic("message").asInstanceOf[String])
        document.getElementById("transaction-name").asInstanceOf[html.Input].placeholder = "Transaction name"
        document.getElementById("transaction-name").asInstanceOf[html.Input].value = ""
        document.getElementById("transaction-budget-selector").innerHTML = "Choose"
        document.getElementById("transaction-amount").asInstanceOf[html.Input].value = ""
        budgetID = 0
        list()
      case Failure(e: ext.AjaxException) =>
        Utils.addAlert("danger", js.JSON.parse(e.xhr.responseText).selectDynamic("message").asInstanceOf[String])
    }
  }

  @JSExportTopLevel("transactionsSave")
  def save(): Unit = {
    getUpdateValues()

    val transaction = new Models.Transaction(
      editedID,
      name,
      date,
      budgetUpdateID,
      amount
    )

    API.patchTransaction(transaction).onComplete {
      case Success(resp) =>
        Utils.addAlert("success", js.JSON.parse(resp.responseText).selectDynamic("message").asInstanceOf[String])
        list()
      case Failure(e: ext.AjaxException) =>
        Utils.addAlert("danger", js.JSON.parse(e.xhr.responseText).selectDynamic("message").asInstanceOf[String])
    }
  }
}
