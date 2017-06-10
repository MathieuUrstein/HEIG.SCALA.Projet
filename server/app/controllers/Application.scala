package controllers

 import play.api.mvc._

class Application extends Controller {
  def dashboard = Action {
    Ok(views.html.pages.dashboard())
  }

  def exchanges = Action {
    Ok(views.html.pages.exchanges())
  }

  def transactions = Action {
    Ok(views.html.pages.transactions())
  }

  def budgets = Action {
    Ok(views.html.pages.budgets())
  }

  def config = Action {
    Ok(views.html.pages.config())
  }

  def login = Action {
    Ok(views.html.pages.login())
  }
}
