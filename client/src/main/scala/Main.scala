import scala.scalajs.js
import org.scalajs.dom
import pages._
import Utils._

object Main extends js.JSApp {

  val pathname: String = dom.document.location.pathname

  def main(): Unit = {
    checkConnection()

    pathname match {
      case "/login" => new Login
      case "/transactions" =>
        Utils.setRange()
        Utils.refresh = Transactions.list
        new Transactions()
      case "/exchanges" =>
        Utils.setRange()
        Utils.refresh = Exchanges.list
        new Exchanges
      case "/budgets" =>
        new Budgets
      case _ =>
        new Dashboard
    }
  }
}