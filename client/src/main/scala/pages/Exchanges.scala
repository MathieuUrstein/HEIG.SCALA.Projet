package pages

import API.Models
import org.scalajs.dom._
import scala.concurrent._
import ExecutionContext.Implicits.global
import scala.scalajs.js
import scala.scalajs.js.JSON
import scala.scalajs.js.annotation.JSExportTopLevel
import scala.util.{Failure, Success}

class Exchanges {
  Exchanges.list()
}

object Exchanges {

  var `type`: String = "Borrow"
  var name: String = ""
  var amount: Double = 0.0
  var date: Models.Date = Utils.now
  var editedID: Int = 0

  private val lendWrapper = document.getElementById("lend-wrapper").asInstanceOf[html.Table]
  private val borrowWrapper = document.getElementById("borrow-wrapper").asInstanceOf[html.Table]

  private def getValues(): Unit = {
    name = document.getElementById("exchange-name").asInstanceOf[html.Input].value
    amount = document.getElementById("exchange-amount").asInstanceOf[html.Input].value.toDouble
    if (
      document.getElementById("borrow-tab-link").asInstanceOf[html.Div].className.contains("active")
    ) `type` = "Borrow"
    else `type` = "Lend"
  }

  def list(): Unit = {
    var borrowSum = 0.0
    var lendSum = 0.0

    borrowWrapper.innerHTML = ""
    lendWrapper.innerHTML = ""

    API.getExchanges.onComplete {
      case Success(resp) =>
        val exchanges = js.JSON.parse(resp.responseText).asInstanceOf[js.Array[Models.Exchange]]
        exchanges.foreach(exchange =>
          exchange.`type` match {
            case "Borrow" =>
              addBorrow(exchange)
              borrowSum += exchange.amount
            case _ =>
              addLend(exchange)
              lendSum += exchange.amount
          })
        var borrowPercent = 0

        if (borrowSum + lendSum == 0) {
          borrowPercent = 50
        } else if (borrowSum == 0) {
          borrowPercent = 0
        } else if(lendSum == 0) {
          borrowPercent = 100
        } else {
          borrowPercent = (borrowSum / (lendSum+borrowSum) * 100).toInt
        }
        document.getElementById("global-stats-resume").asInstanceOf[html.Div].innerHTML =
          "Borrow " + borrowPercent + "% - " + (100 - borrowPercent) + "% Lend"
        document.getElementById("progress-borrow").asInstanceOf[html.Div].style.width = borrowPercent + "%"
        document.getElementById("progress-lend").asInstanceOf[html.Div].style.width = (100 - borrowPercent) + "%"
        document.getElementById("progress-borrow").asInstanceOf[html.Div].innerHTML = borrowSum + " CHF"
        document.getElementById("progress-lend").asInstanceOf[html.Div].innerHTML = lendSum + " CHF"
      case Failure(e: ext.AjaxException) =>
        Utils.addAlert("danger", js.JSON.parse(e.xhr.responseText).selectDynamic("message").asInstanceOf[String])
    }
  }

  def addBorrow(exchange: Models.Exchange): Unit = {
    val row =   "<td width=\"5%\" class=\"edit\"><button onclick=\"exchangesSetEditedID(" + exchange.id + ")\" class=\"btn\" data-toggle=\"modal\" data-target=\"#exchanges-modal\"><i class=\"fa fa-ellipsis-v\" aria-hidden=\"true\"></i></button></td>" +
      "<td width=\"65%\" class=\"name\">" + exchange.name + " <span class=\"date\">" + Utils.prettyDate(exchange.date) + "</span></td>" +
      "<td width=\"30%\" class=\"amount text-right\">"+ exchange.amount +" CHF</td>"
    val tr = document.createElement("tr").asInstanceOf[html.TableRow]
    tr.innerHTML = row
    borrowWrapper.appendChild(tr)
  }

  def addLend(exchange: Models.Exchange): Unit = {
    val row = "<td width=\"30%\" class=\"amount\">"+ exchange.amount +" CHF</td>\n" +
      "<td width=\"70%\" class=\"name text-right\">" + exchange.name + " <span class=\"date\">" + Utils.prettyDate(exchange.date) + "</span></td>\n" +
      "<td width=\"5%\" class=\"edit\"><button onclick=\"exchangesSetEditedID(" + exchange.id + ")\" class=\"btn\" data-toggle=\"modal\" data-target=\"#exchanges-modal\"><i class=\"fa fa-ellipsis-v\" aria-hidden=\"true\"></i></button></td>"
    val tr = document.createElement("tr").asInstanceOf[html.TableRow]
    tr.innerHTML = row
    lendWrapper.appendChild(tr)
  }

  @JSExportTopLevel("exchangesSetEditedID")
  def setEditedID(id: Int): Unit = {
    editedID = id

    id match {
      case 0 =>
        name = ""
        amount = 0.0
        date = Utils.now

        document.getElementById("exchange-name").asInstanceOf[html.Input].value = ""
        document.getElementById("exchange-amount").asInstanceOf[html.Input].value = ""
      case _ =>
        API.getExchange(id).onComplete {
          case Success(resp) =>
            val exchange = JSON.parse(resp.responseText).asInstanceOf[Models.Exchange]
            `type` = exchange.`type`
            name = exchange.name
            amount = exchange.amount
            date = exchange.date

            document.getElementById("exchange-name").asInstanceOf[html.Input].value = name
            document.getElementById("exchange-amount").asInstanceOf[html.Input].value = amount.toString

            `type` match {
              case "Borrow" =>
                document.getElementById("borrow-tab-link").asInstanceOf[html.Div].className = "nav-link active"
                document.getElementById("lend-tab-link").asInstanceOf[html.Div].className = "nav-link"
              case _ =>
                document.getElementById("borrow-tab-link").asInstanceOf[html.Div].className = "nav-link"
                document.getElementById("lend-tab-link").asInstanceOf[html.Div].className = "nav-link active"
            }
          case Failure(e: ext.AjaxException) =>
            Utils.addAlert("danger", js.JSON.parse(e.xhr.responseText).selectDynamic("message").asInstanceOf[String])
        }
    }
  }

  @JSExportTopLevel("exchangesDelete")
  def delete(): Unit = {
    if(editedID != 0) {
      API.deleteExchange(editedID).onComplete {
        case Success(resp) =>
          Utils.addAlert("success", js.JSON.parse(resp.responseText).selectDynamic("message").asInstanceOf[String])
          list()
        case Failure(e: ext.AjaxException) =>
          Utils.addAlert("danger", js.JSON.parse(e.xhr.responseText).selectDynamic("message").asInstanceOf[String])
      }
    }
  }

  @JSExportTopLevel("exchangesSave")
  def save(): Unit = {
    getValues()

    val exchange = new Models.Exchange(
      editedID,
      name,
      date,
      `type`,
      amount
    )

    editedID match {
      case 0 =>
        API.postExchange(exchange).onComplete {
          case Success(resp) =>
            Utils.addAlert("success", js.JSON.parse(resp.responseText).selectDynamic("message").asInstanceOf[String])
            list()
          case Failure(e: ext.AjaxException) =>
            Utils.addAlert("danger", js.JSON.parse(e.xhr.responseText).selectDynamic("message").asInstanceOf[String])
        }
      case _ =>
        API.patchExchange(exchange).onComplete {
          case Success(resp) =>
            Utils.addAlert("success", js.JSON.parse(resp.responseText).selectDynamic("message").asInstanceOf[String])
            list()
          case Failure(e: ext.AjaxException) =>
            Utils.addAlert("danger", js.JSON.parse(e.xhr.responseText).selectDynamic("message").asInstanceOf[String])
        }
    }
  }
}
