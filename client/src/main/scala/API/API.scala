import org.scalajs.dom.ext.Ajax
import scala.scalajs.js.JSON
import API.Models._
import scala.concurrent._
import ExecutionContext.Implicits.global
import org.scalajs.dom

package object API {

  private val prefix = "/api/"

  private object urls {
    val register: String = prefix + "register"
    val auth: String = prefix + "auth"
    val user: String = prefix + "user"
    val transactions: String = prefix + "transactions"
    val exchanges: String = prefix + "exchanges"
    val budgets: String = prefix + "budgets"
  }

  var headers: Map[String, String] = Map(
    "Content-Type" -> "application/json; charset=utf-8"
  )

  def register(user: User): Future[dom.XMLHttpRequest] = {
    Ajax.post(urls.register, JSON.stringify(user), headers=headers)
  }

  def login(credentials: Credentials): Future[dom.XMLHttpRequest] = {
    Ajax.post(urls.auth, JSON.stringify(credentials), headers=headers)
  }

  /*
  def getUser(): Unit = {
    Ajax.get(urls.user)
  }

  def putUser(user: User): Unit = {
    Ajax.put(urls.user, JSON.stringify(user))
  }

  def deleteUser(): Unit = {
    Ajax.delete(urls.user)
  }

  def getTransactions(dateRange: DateRange): Unit = {
    Ajax.get(urls.transactions)
  }

  def postTransaction(transaction: Transaction): Unit = {
    Ajax.post(urls.transactions, JSON.stringify(transaction))
  }

  def getTransaction(id: Int): Unit = {
    Ajax.get(urls.transactions + "/" + id)
  }

  def putTransaction(transaction: Transaction): Unit = {
    Ajax.put(urls.transactions + "/" + transaction.id, JSON.stringify(transaction))
  }
  */
}
