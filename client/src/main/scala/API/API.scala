import API.Models._
import org.scalajs.dom
import org.scalajs.dom.XMLHttpRequest
import org.scalajs.dom.ext.Ajax

import scala.concurrent._
import scala.scalajs.js

package object API {
  private val prefix = "/api/"

  private object urls {
    val register: String = prefix + "register"
    val auth: String = prefix + "auth"
    val user: String = prefix + "user"
    val transactions: String = prefix + "transactions"
    val exchanges: String = prefix + "exchanges"
    val budgets: String = prefix + "budgets"
    val usage: String = prefix + "dashboard/usage"
    val spendings: String = prefix + "dashboard/spendings"
  }

  object SuperAjax {
    def patch(
               url: String, data: js.Any = null, timeout: Int = 0,
               headers: Map[String, String] = headers,
               withCredentials: Boolean = false, responseType: String = ""
             ): Future[XMLHttpRequest] = {
      Ajax("PATCH", url, js.JSON.stringify(data), timeout, headers, withCredentials, responseType)
    }

    def get(
             url: String, data: js.Any = null, timeout: Int = 0,
             headers: Map[String, String] = headers,
             withCredentials: Boolean = false, responseType: String = ""
           ): Future[XMLHttpRequest] = {
      Ajax("GET", url, js.JSON.stringify(data), timeout, headers, withCredentials, responseType)
    }

    def post(
              url: String, data: js.Any = null, timeout: Int = 0,
              headers: Map[String, String] = headers,
              withCredentials: Boolean = false, responseType: String = ""
            ): Future[XMLHttpRequest] = {
      Ajax("POST", url, js.JSON.stringify(data), timeout, headers, withCredentials, responseType)
    }

    def delete(
                url: String, data: js.Any = null, timeout: Int = 0,
                headers: Map[String, String] = headers,
                withCredentials: Boolean = false, responseType: String = ""
              ): Future[XMLHttpRequest] = {
      Ajax("DELETE", url, js.JSON.stringify(data), timeout, headers, withCredentials, responseType)
    }

    def options(
                 url: String, data: js.Any = Utils.getRange, timeout: Int = 0,
                 headers: Map[String, String] = headers,
                 withCredentials: Boolean = false, responseType: String = ""
               ): Future[XMLHttpRequest] = {
      Ajax("OPTIONS", url, js.JSON.stringify(data), timeout, headers, withCredentials, responseType)
    }
  }

  var headers: Map[String, String] = Map(
    "Content-Type" -> "application/json"
  )

  def register(user: User): Future[dom.XMLHttpRequest] = {
    SuperAjax.post(urls.register, user)
  }

  def login(credentials: Credentials): Future[dom.XMLHttpRequest] = {
    SuperAjax.post(urls.auth, credentials)
  }

  def getUsage: Future[dom.XMLHttpRequest] = {
    SuperAjax.options(urls.usage)
  }

  def getSpenings: Future[dom.XMLHttpRequest] = {
    SuperAjax.options(urls.spendings)
  }

  def postExchange(exchange: Exchange): Future[dom.XMLHttpRequest] = {
    SuperAjax.post(urls.exchanges, exchange)
  }

  def getExchanges: Future[dom.XMLHttpRequest] = {
    SuperAjax.options(urls.exchanges)
  }

  def getExchange(id: Int): Future[dom.XMLHttpRequest] = {
    SuperAjax.get(urls.exchanges + "/" + id)
  }

  def patchExchange(exchange: Exchange): Future[dom.XMLHttpRequest] = {
    SuperAjax.patch(urls.exchanges + "/" + exchange.id, exchange)
  }

  def deleteExchange(id: Int): Future[dom.XMLHttpRequest] = {
    SuperAjax.delete(urls.exchanges + "/" + id)
  }

  def getBudgets: Future[dom.XMLHttpRequest] = {
    SuperAjax.get(urls.budgets)
  }

  def postBudget(budget: Budget): Future[dom.XMLHttpRequest] = {
    SuperAjax.post(urls.budgets, budget)
  }

  def deleteBudget(id: Int): Future[dom.XMLHttpRequest] = {
    SuperAjax.delete(urls.budgets + "/" + id)
  }

  def getBudget(id: Int): Future[dom.XMLHttpRequest] = {
    SuperAjax.get(urls.budgets + "/" + id)
  }

  def patchBudget(budget: Budget): Future[dom.XMLHttpRequest] = {
    SuperAjax.patch(urls.budgets + "/" + budget.id, budget)
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
  }*/

  def postTransaction(transaction: Transaction): Future[dom.XMLHttpRequest] = {
    SuperAjax.post(urls.transactions, transaction)
  }

  def getTransactions: Future[dom.XMLHttpRequest] = {
    SuperAjax.options(urls.transactions)
  }

  def getTransaction(id: Int): Future[dom.XMLHttpRequest] = {
    SuperAjax.get(urls.transactions + "/" + id)
  }

  def patchTransaction(transaction: Transaction): Future[dom.XMLHttpRequest] = {
    SuperAjax.patch(urls.transactions + "/" + transaction.id, transaction)
  }

  def deleteTransaction(id: Int): Future[dom.XMLHttpRequest] = {
    SuperAjax.delete(urls.transactions + "/" + id)
  }
}
