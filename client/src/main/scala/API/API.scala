import org.scalajs.dom.ext.Ajax

import scala.scalajs.js.JSON

package object API {

  private val prefix = "/api/"

  class Status {

  }

  object urls {
    val register: String = prefix + "register"
    val auth: String = prefix + "auth"
    val user: String = prefix + "user"
    val transactions: String = prefix + "transactions"
    val exchanges: String = prefix + "exchanges"
    val budgets: String = prefix + "budgets"
  }

  def register(): Unit = {
    Ajax.post(urls.register, JSON.stringify("TODO"))
  }
}
