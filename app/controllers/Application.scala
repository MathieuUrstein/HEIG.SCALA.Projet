package controllers

import javax.inject.Inject

import dao.UserDAO
import models.User
import play.api.libs.functional.syntax._
import play.api.libs.json._
import play.api.mvc._

import scala.concurrent.ExecutionContext

class Application @Inject() (userDAO: UserDAO)(implicit executionContext: ExecutionContext) extends Controller {
  implicit val userReads: Reads[User] = (
    (JsPath \ "lastName").read[String] and
      (JsPath \ "firstName").read[String] and
      (JsPath \ "email").read[String] and
      (JsPath \ "password").read[String] and
      (JsPath \ "currency").read[String]
    )(User.apply _)

  implicit val userWrites: Writes[User] = (
    (JsPath \ "lastName").write[String] and
      (JsPath \ "firstName").write[String] and
      (JsPath \ "email").write[String] and
      (JsPath \ "password").write[String] and
      (JsPath \ "currency").write[String]
    )(unlift(User.unapply))

  def index = Action {
    Ok(views.html.index("Hello World !"))
  }

  def admin = Action {
    Ok(views.html.index("Test Admin !"))
  }

  /*def login: Action[JsValue] = Action(BodyParsers.parse.json) { implicit request =>
    val result = request.body.validate[LoginForm]

    result.fold(
      errors => BadRequest(Json.obj("status" -> "ERROR", "message" -> JsError.toJson(errors))),
      login => {
        val username = login.username
        val password = login.password

        Ok(Json.obj("status" -> "OK", "message" -> (username + ":" + password)))
      }
    )
  }*/


  def insertUser(): Action[JsValue] = Action(BodyParsers.parse.json) { request =>
    val result = request.body.validate[User]

    result.fold(
      errors => BadRequest(Json.obj("status" -> "ERROR", "message" -> JsError.toJson(errors))),
      user => {
        // TODO : probleme avec doublon et autre (aucun erreur levee)
        userDAO.insert(user)

        Ok(Json.obj("status" -> "OK", "message" -> (user.email + ":" + user.password + " created")))
      }
    )
  }

  /*def getUser = Action {
    Ok(Json.obj("status" -> "OK", "user" -> user))
  }*/
}
