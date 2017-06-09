import scala.scalajs.js
import org.scalajs.dom
import pages._
import Utils._

object Main extends js.JSApp {

  def main(): Unit = {
    checkConnection()

    dom.document.location.pathname match {
      case "/login" => new Login
      case _ => ()
    }
  }
}