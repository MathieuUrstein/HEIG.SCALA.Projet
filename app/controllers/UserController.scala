package controllers

import java.sql.SQLIntegrityConstraintViolationException
import javax.inject.Inject

import dao.UserDAO
import models.User
import org.sqlite.SQLiteException
import play.api.libs.functional.syntax._
import play.api.libs.json._
import play.api.mvc._
import utils.Const

import scala.concurrent.{ExecutionContext, Future}

class UserController @Inject()(userDAO: UserDAO)(implicit executionContext: ExecutionContext) extends Controller {
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


  def insertUser(): Action[JsValue] = Action.async(BodyParsers.parse.json) { request =>
    val result = request.body.validate[User]

    result.fold(
      errors => Future.successful {
        BadRequest(Json.obj("status" -> "ERROR", "message" -> JsError.toJson(errors)))
      },
      user => {
        userDAO.insert(user).map { _ =>
          Ok(Json.obj("status" -> "OK", "message" -> (user.email + ":" + user.password + " created")))
        }.recover {
          // case in an error of conflict with the user email
          case e: SQLiteException if e.getResultCode.code == Const.SQLiteUniqueConstraintErrorCode =>
            Conflict(Json.obj("status" -> "ERROR", "message" -> "'%s' email already exists".format(user.email)))
        }
      }
    )
  }

  /*def getUser = Action {
    Ok(Json.obj("status" -> "OK", "user" -> user))
  }*/
}
