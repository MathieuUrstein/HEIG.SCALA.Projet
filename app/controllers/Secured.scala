package controllers

import models.{DateDTO, FromToDatesDTO}
import pdi.jwt.JwtSession._
import play.api.data.validation.ValidationError
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.functional.syntax.unlift
import play.api.libs.json.{JsPath, Json, Reads, Writes}
import play.api.mvc.Results._
import play.api.mvc._
import utils.Const
import play.api.libs.functional.syntax._

import scala.concurrent.Future

class AuthenticatedRequest[A](userEmail: String, request: Request[A]) extends WrappedRequest[A](request)

trait Secured {
  def Authenticated = AuthenticatedAction

  // defines a custom reads to be reused
  // a reads that verifies your value is not equal to a given value
  // used to refuse empty string in JSON in our case
  def notEqual[T](message: String, value: T)(implicit r: Reads[T]): Reads[T] = {
    Reads.filterNot(ValidationError(message, value))(_ == value)
  }

  implicit val dateDTOReads: Reads[DateDTO] = (
    (JsPath \ "day").read[Int] and
      (JsPath \ "month").read[Int] and
      (JsPath \ "year").read[Int]
    ) (DateDTO.apply _)

  implicit val dateDTOWrites: Writes[DateDTO] = (
    (JsPath \ "day").write[Int] and
      (JsPath \ "month").write[Int] and
      (JsPath \ "year").write[Int]
    ) (unlift(DateDTO.unapply))

  implicit val fromToDatesDTOReads: Reads[FromToDatesDTO] = (
    (JsPath \ "from").readNullable[DateDTO] and
      (JsPath \ "to").readNullable[DateDTO]
    ) (FromToDatesDTO.apply _)
}

object AuthenticatedAction extends ActionBuilder[AuthenticatedRequest] {
  def invokeBlock[A](request: Request[A], block: AuthenticatedRequest[A] => Future[Result]): Future[Result] = {
    request.jwtSession.getAs[String](Const.ValueStoredJWT) match {
      case Some(user) => block(new AuthenticatedRequest(user, request)).map(_.refreshJwtSession(request))
      case _ => Future.successful(Unauthorized(Json.obj("status" -> "ERROR", "message" -> "you are not logged in")))
    }
  }
}
