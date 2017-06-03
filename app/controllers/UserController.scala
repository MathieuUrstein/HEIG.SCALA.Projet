package controllers

import javax.inject.Inject

import dao.UserDAO
import models.{PasswordDTO, User, UserDTO}
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
    ) (User.apply _)

  implicit val userDTOReads: Reads[UserDTO] = (
    (JsPath \ "lastName").read[String] and
      (JsPath \ "firstName").read[String] and
      (JsPath \ "email").read[String] and
      (JsPath \ "currency").read[String]
    ) (UserDTO.apply _)

  implicit val userDTOWrites: Writes[UserDTO] = (
    (JsPath \ "lastName").write[String] and
      (JsPath \ "firstName").write[String] and
      (JsPath \ "email").write[String] and
      (JsPath \ "currency").write[String]
    ) (unlift(UserDTO.unapply))

  implicit val passwordDTOReads: Reads[PasswordDTO] = (
    (JsPath \ "oldPassword").read[String] and
      (JsPath \ "newPassword").read[String]
    ) (PasswordDTO.apply _)

  def index: Action[AnyContent] = Action.async {
    userDAO.all().map { users =>
      Ok(Json.obj("status" -> "OK", "users" -> users))
    }
  }

  def create(): Action[JsValue] = Action.async(BodyParsers.parse.json) { request =>
    val result = request.body.validate[User]

    result.fold(
      errors => Future.successful {
        BadRequest(Json.obj("status" -> "ERROR", "message" -> JsError.toJson(errors)))
      },
      user => {
        userDAO.insert(user).map { _ =>
          Created(Json.obj("status" -> "OK", "message" -> "user '%s' created".format(user.email)))
        }.recover {
          // case in an error of conflict with the user email
          case e: SQLiteException if e.getResultCode.code == Const.SQLiteUniqueConstraintErrorCode =>
            Conflict(Json.obj("status" -> "ERROR", "message" -> "user '%s' already exists".format(user.email)))
        }
      }
    )
  }

  def read(id: String): Action[AnyContent] = Action.async {
    userDAO.find(id.toInt).map { user =>
      Ok(Json.obj("status" -> "OK", "user" -> user))
    }.recover {
      // case in not found the specified user with its id
      case _: NoSuchElementException =>
        NotFound(Json.obj("status" -> "ERROR", "message" -> "user with id '%s' doesn't exist".format(id)))
    }
  }

  def fullUpdate(id: String): Action[JsValue] = Action.async(BodyParsers.parse.json) { request =>
    val result = request.body.validate[UserDTO]

    result.fold(
      errors => Future.successful {
        BadRequest(Json.obj("status" -> "ERROR", "message" -> JsError.toJson(errors)))
      },
      user => {
        userDAO.fullUpdate(id.toInt, user).map { _ =>
          Ok(Json.obj("status" -> "OK", "message" -> "user with id '%s' updated".format(id)))
        }.recover {
          // case in not found the specified user with its id
          case _: NoSuchElementException =>
            NotFound(Json.obj("status" -> "ERROR", "message" -> "user with id '%s' doesn't exist".format(id)))
          // case in an error of conflict with the new user email
          case e: SQLiteException if e.getResultCode.code == Const.SQLiteUniqueConstraintErrorCode =>
            Conflict(Json.obj("status" -> "ERROR", "message" -> "user '%s' already exists".format(user.email)))
        }
      }
    )
  }

  def updatePassword(id: String): Action[JsValue] = Action.async(BodyParsers.parse.json) { request =>
   val result = request.body.validate[PasswordDTO]

   result.fold(
     errors => Future.successful {
       BadRequest(Json.obj("status" -> "ERROR", "message" -> JsError.toJson(errors)))
     },
     password => {
       // we verify that passwords correspond
       userDAO.getPassword(id.toInt).map { p =>
         if (p.password.equals(password.oldPassword)) {
           userDAO.updatePassword(id.toInt, password.newPassword).map { _ => () }
           Ok(Json.obj("status" -> "OK", "message" -> "password of user with id '%s' updated".format(id)))
         }
         else {
           BadRequest(Json.obj("status" -> "ERROR", "message" -> "the old password is false"))
         }
       }.recover {
         // case in not found the specified user with its id
         case _: NoSuchElementException =>
           NotFound(Json.obj("status" -> "ERROR", "message" -> "user with id '%s' doesn't exist".format(id)))
       }
     }
   )
 }

  def delete(id: String): Action[AnyContent] = Action.async {
    userDAO.delete(id.toInt).map { _ =>
      Ok(Json.obj("status" -> "OK", "user" -> "user with id '%s' deleted".format(id)))
    }.recover {
      // case in not found the specified user with its id
      case _: NoSuchElementException =>
        NotFound(Json.obj("status" -> "ERROR", "message" -> "user with id '%s' doesn't exist".format(id)))
    }
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
}
