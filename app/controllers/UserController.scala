package controllers

import javax.inject.Inject

import dao.UserDAO
import models.{LoginFormDTO, User, UserGETDTO, UserPATCHDTO}
import org.mindrot.jbcrypt.BCrypt
import org.sqlite.SQLiteException
import pdi.jwt.JwtSession._
import play.api.libs.functional.syntax._
import play.api.libs.json._
import play.api.mvc._
import utils.Const

import scala.concurrent.{ExecutionContext, Future}

class UserController @Inject()(userDAO: UserDAO)(implicit executionContext: ExecutionContext)
  extends Controller with Secured {
  implicit val userReads: Reads[User] = (
    (JsPath \ "fullname").read[String](notEqual("")) and
      (JsPath \ "email").read[String](notEqual("")) and
      (JsPath \ "password").read[String](notEqual("")) and
      (JsPath \ "currency").read[String](notEqual(""))
    ) (User.apply _)

  implicit val userGETDTOWrites: Writes[UserGETDTO] = (
    (JsPath \ "fullname").write[String] and
      (JsPath \ "email").write[String] and
      (JsPath \ "currency").write[String]
    ) (unlift(UserGETDTO.unapply))

  implicit val userPATCHDTOReads: Reads[UserPATCHDTO] = (
    (JsPath \ "fullname").read[String] and
      (JsPath \ "email").read[String] and
      (JsPath \ "password").read[String] and
      (JsPath \ "currency").read[String]
    ) (UserPATCHDTO.apply _)

  implicit val loginFormDTOReads: Reads[LoginFormDTO] = (
    (JsPath \ "email").read[String](notEqual("")) and
      (JsPath \ "password").read[String](notEqual(""))
    ) (LoginFormDTO.apply _)

  def create(): Action[JsValue] = Action.async(BodyParsers.parse.json) { implicit request =>
    val result = request.body.validate[User]

    result.fold(
      errors => Future.successful {
        BadRequest(Json.obj("status" -> "ERROR", "message" -> JsError.toJson(errors)))
      },
      user => {
        userDAO.insert(user).map { _ =>
          // we directly log in the user with a JWT
          // the JWT is returned in the header name "Authorization" (request) by default
          // we add the unique email of the user in the JWT to identify him
          Created(Json.obj("status" -> "OK", "message" -> "user '%s' created".format(user.email)))
            .addingToJwtSession(Const.ValueStoredJWT, user.email)
        }.recover {
          // case in an error of conflict with the user email
          case e: SQLiteException if e.getResultCode.code == Const.SQLiteUniqueConstraintErrorCode =>
            Conflict(Json.obj("status" -> "ERROR", "message" -> "email '%s' already exists".format(user.email)))
        }
      }
    )
  }

  // TODO : cas ou l'utilisateur est deja authentifie => erreur 400
  // TODO : amelioration pour invalider un JWT

  def login: Action[JsValue] = Action.async(BodyParsers.parse.json) { implicit request =>
    val result = request.body.validate[LoginFormDTO]

    result.fold(
      errors => Future.successful {
        BadRequest(Json.obj("status" -> "ERROR", "message" -> JsError.toJson(errors)))
      },
      login => {
        userDAO.getPassword(login.email).map { correctPasswordHash =>
          if (BCrypt.checkpw(login.password, correctPasswordHash)) {
            // we add the unique email of the user in the JWT to identify him
            Ok(Json.obj("status" -> "OK", "message" -> "user '%s' logged".format(login.email)))
              .addingToJwtSession(Const.ValueStoredJWT, login.email)
          }
          else {
            Unauthorized(Json.obj("status" -> "ERROR", "message" -> "authentication failed"))
          }
        }.recover {
          // case in not found the specified user with its email
          // we throw the same error if the password is not valid
          case _: NoSuchElementException =>
            Unauthorized(Json.obj("status" -> "ERROR", "message" -> "authentication failed"))
        }
      }
    )
  }

  def read: Action[AnyContent] = Authenticated.async { implicit request =>
    // we look for the user email in the JWT
    userDAO.find(request.jwtSession.getAs[String](Const.ValueStoredJWT).get).map { user =>
      Ok(Json.obj("status" -> "OK", "user" -> user))
    }
  }

  def update: Action[JsValue] = Authenticated.async(BodyParsers.parse.json) { implicit request =>
    val result = request.body.validate[UserPATCHDTO]

    result.fold(
      errors => Future.successful {
        BadRequest(Json.obj("status" -> "ERROR", "message" -> JsError.toJson(errors)))
      },
      user => {
        // we look for the user email in the JWT
        userDAO.update(request.jwtSession.getAs[String](Const.ValueStoredJWT).get, user).map { _ =>
          // in case of a successful change of email, we must change the value contained in the JWT with the new email
          if (!user.email.isEmpty) {
            Ok(Json.obj("status" -> "OK", "message" -> "information updated"))
              .addingToJwtSession(Const.ValueStoredJWT, user.email)
          }
          else {
            Ok(Json.obj("status" -> "OK", "message" -> "information updated"))
          }
        }.recover {
          // case in an error of conflict with the new user email
          case e: SQLiteException if e.getResultCode.code == Const.SQLiteUniqueConstraintErrorCode =>
            Conflict(Json.obj("status" -> "ERROR", "message" -> "user '%s' already exists".format(user.email)))
        }
      }
    )
  }

  def delete: Action[AnyContent] = Authenticated.async { implicit request =>
    // we look for the user email in the JWT
    userDAO.delete(request.jwtSession.getAs[String](Const.ValueStoredJWT).get).map { _ =>
      Ok(Json.obj("status" -> "OK", "user" -> "user deleted"))
    }
  }
}