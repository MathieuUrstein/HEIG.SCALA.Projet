package controllers

import javax.inject.Inject

import dao.DashboardDAO
import models._
import pdi.jwt.JwtSession._
import play.api.libs.functional.syntax._
import play.api.libs.json._
import play.api.mvc._
import utils.Const

import scala.concurrent.{ExecutionContext, Future}

class DashboardController @Inject()(dashboardDAO: DashboardDAO)(implicit executionContext: ExecutionContext)
  extends Controller with Secured {
  implicit val spendingGETDTOWrites: Writes[SpendingGETDTO] = (
    (JsPath \ "date").write[DateDTO] and
      (JsPath \ "budget").write[String] and
      (JsPath \ "amount").write[Double]
    ) (unlift(SpendingGETDTO.unapply))

  implicit val usageGETDTOWrites: Writes[UsageGETDTO] = (
    (JsPath \ "date").write[DateDTO] and
      (JsPath \ "used").write[Double] and
      (JsPath \ "left").write[Double]
    ) (unlift(UsageGETDTO.unapply))

  def spendings: Action[JsValue] = Authenticated.async(BodyParsers.parse.json) { implicit request =>
    val result = request.body.validate[FromToDatesDTO]

    result.fold(
      errors => Future.successful {
        BadRequest(Json.obj("status" -> "ERROR", "message" -> JsError.toJson(errors)))
      },
      dates => {
        // we look for the user email in the JWT
        dashboardDAO.findSpendings(request.jwtSession.getAs[String](Const.ValueStoredJWT).get, dates).map { spendings =>
          Ok(Json.toJson(spendings))
        }
      }
    )
  }

  def usage: Action[JsValue] = Authenticated.async(BodyParsers.parse.json) { implicit request =>
    val result = request.body.validate[FromToDatesDTO]

    result.fold(
      errors => Future.successful {
        BadRequest(Json.obj("status" -> "ERROR", "message" -> JsError.toJson(errors)))
      },
      dates => {
        // we look for the user email in the JWT
        dashboardDAO.findUsage(request.jwtSession.getAs[String](Const.ValueStoredJWT).get, dates).map { usages =>
          Ok(Json.toJson(usages))
        }
      }
    )
  }
}
