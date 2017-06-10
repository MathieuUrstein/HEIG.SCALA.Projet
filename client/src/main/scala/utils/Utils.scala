import org.scalajs.dom._
import API._
import org.scalajs.dom.html
import scala.scalajs.js

import scala.scalajs.js.annotation.JSExportTopLevel

package object Utils {
  def checkConnection(): Unit = {
    document.location.pathname match {
      case "/login" => ()
      case _ =>
        val cookies = document.cookie
          .split(";")
          .map(cookie => {
            val keyVal = cookie.split("=")
            if (cookie == "") {
              "" -> ""
            } else {
              keyVal(0) -> keyVal(1)
            }
          })
          .toMap
        if (cookies.contains("Authorization")) {
          headers += ("Authorization" -> cookies.getOrElse("Authorization", ""))
        } else {
          window.location.pathname = "/login"
        }
    }
  }

  var refresh: () => Unit = () => {}

  def now: Models.Date = {
    val date = new js.Date()
    new Models.Date(
      date.getDate(),
      date.getMonth() + 1,
      date.getFullYear()
    )
  }
  var fromMonth: Int = now.month
  var fromYear: Int = now.year
  var toMonth: Int = (now.month + 1) % 12
  var toYear: Int = now.year

  def monthIntToStr(month: Int): String = month match {
    case 1 => "January"
    case 2 => "February"
    case 3 => "March"
    case 4 => "April"
    case 5 => "Mai"
    case 6 => "June"
    case 7 => "July"
    case 8 => "August"
    case 9 => "September"
    case 10 => "October"
    case 11 => "November"
    case _ => "December"
  }

  def monthStrToInt(month: String): Int = month match {
    case "January" => 1
    case "February" => 2
    case "March" => 3
    case "April" => 4
    case "Mai" => 5
    case "June" => 6
    case "July" => 7
    case "August" => 8
    case "September" => 9
    case "October" => 10
    case "November" => 11
    case _ => 12
  }

  def prettyMonth(month: Int): String = month match {
    case 1 => "jan"
    case 2 => "feb"
    case 3 => "mar"
    case 4 => "apr"
    case 5 => "may"
    case 6 => "jun"
    case 7 => "jul"
    case 8 => "aug"
    case 9 => "sep"
    case 10 => "oct"
    case 11 => "nov"
    case _ => "dec"
  }

  def prettyDate(date: Models.Date): String = {
    date.day.toString + " " +
      prettyMonth(date.month) + " " +
      date.year.toString
  }

  @JSExportTopLevel("setFromMonth")
  def setFromMonth(elem: html.Link): Unit = {
    fromMonth = monthStrToInt(elem.innerHTML)
    setRange()
    refresh()
  }
  @JSExportTopLevel("setFromYear")
  def setFromYear(elem: html.Link): Unit = {
    fromYear = elem.innerHTML.toInt
    setRange()
    refresh()
  }
  @JSExportTopLevel("setToMonth")
  def setToMonth(elem: html.Link): Unit = {
    toMonth = monthStrToInt(elem.innerHTML)
    setRange()
    refresh()
  }
  @JSExportTopLevel("setToYear")
  def setToYear(elem: html.Link): Unit = {
    toYear = elem.innerHTML.toInt
    setRange()
    refresh()
  }

  def getRange: Models.DateRange = {
    new Models.DateRange(
      new Models.Date(
        1,
        fromMonth,
        fromYear
      ),
      new Models.Date(
        1,
        toMonth,
        toYear
      )
    )
  }

  def setRange(): Unit = {
    document.getElementById("from-month").innerHTML = monthIntToStr(fromMonth)
    document.getElementById("from-year").innerHTML = fromYear.toString
    document.getElementById("to-month").innerHTML = monthIntToStr(toMonth)
    document.getElementById("to-year").innerHTML = toYear.toString
  }

  @JSExportTopLevel("disconnect")
  def disconnect(): Unit = {
    document.cookie = "Authorization=; expires=Thu, 01 Jan 1970 00:00:01 GMT;"
    window.location.pathname = "/login"
  }

  def addAlert(`type`: String, content: String): Unit = {
    val wrapper: html.Div = document.getElementById("flash").asInstanceOf[html.Div]

    val alertDiv = document.createElement("div").asInstanceOf[html.Div]
    alertDiv.className = "alert alert-dismissible fade show alert-" + `type`
    alertDiv.innerHTML =  "<button type=\"button\" class=\"close\" data-dismiss=\"alert\" aria-label=\"Close\">" +
      "<span aria-hidden=\"true\">&times;</span>" +
      "</button>" +
      content
    wrapper.appendChild(alertDiv)
  }
}
