package controllers

import pdi.jwt.JwtSession._
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.json.Json
import play.api.mvc.Results._
import play.api.mvc._
import utils.Const

import scala.concurrent.Future

class AuthenticatedRequest[A](userEmail: String, request: Request[A]) extends WrappedRequest[A](request)

trait Secured {
  def Authenticated = AuthenticatedAction
}

object AuthenticatedAction extends ActionBuilder[AuthenticatedRequest] {
  def invokeBlock[A](request: Request[A], block: AuthenticatedRequest[A] => Future[Result]): Future[Result] = {
    request.jwtSession.getAs[String](Const.ValueStoredJWT) match {
      case Some(user) => block(new AuthenticatedRequest(user, request)).map(_.refreshJwtSession(request))
      case _ => Future.successful(Unauthorized(Json.obj("status" -> "ERROR", "message" -> "you are not logged in")))
    }
  }
}
