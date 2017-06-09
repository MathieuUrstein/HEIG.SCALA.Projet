package pages

import API._
import API.Models._
import org.scalajs.dom
import org.scalajs.dom._
import scala.scalajs.js
import scala.concurrent._
import ExecutionContext.Implicits.global
import scala.scalajs.js.JSON
import scala.scalajs.js.annotation.JSExportTopLevel
import scala.util._

class Login {

}

object Login {

  @JSExportTopLevel("login")
  def login(): Unit = {
    val credentials = new Credentials(
      document.getElementById("login-email").asInstanceOf[html.Input].value,
      document.getElementById("login-password").asInstanceOf[html.Input].value
    )
    API.login(credentials).onComplete {
      case Success(req) =>
        val token = req.getResponseHeader("Authorization")
        headers += ("Authorization" -> token)
        document.cookie = "Authorization=" + token
        document.location.pathname = "/"
      case Failure(e: dom.ext.AjaxException) =>
        utils.addAlert("danger", JSON.parse(e.xhr.responseText).selectDynamic("message").asInstanceOf[String])
    }
  }

  @JSExportTopLevel("register")
  def register(): Unit = {
    val user = new User(
      document.getElementById("register-email").asInstanceOf[html.Input].value,
      document.getElementById("register-fullname").asInstanceOf[html.Input].value,
      document.getElementById("register-password").asInstanceOf[html.Input].value,
      document.getElementById("register-currency").asInstanceOf[html.Input].value
    )
    API.register(user).onComplete {
      case Success(req) =>
        val token = req.getResponseHeader("Authorization")
        headers += ("Authorization" -> token)
        document.cookie = "Authorization=" + token
        document.location.pathname = "/"
      case Failure(e: dom.ext.AjaxException) =>
        utils.addAlert("danger", JSON.parse(e.xhr.responseText).selectDynamic("message").asInstanceOf[String])
    }
  }
}