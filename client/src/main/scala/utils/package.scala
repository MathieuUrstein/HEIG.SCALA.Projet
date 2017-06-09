import org.scalajs.dom._
import API._
import scala.scalajs.js.annotation.JSExportTopLevel

package object utils {

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
