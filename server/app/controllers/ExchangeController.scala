package controllers

import javax.inject.Inject

import dao.ExchangeDAO
import models._
import pdi.jwt.JwtSession._
import play.api.libs.functional.syntax._
import play.api.libs.json._
import play.api.mvc._
import utils.Const

import scala.concurrent.{ExecutionContext, Future}

class ExchangeController @Inject()(exchangeDAO: ExchangeDAO)(implicit executionContext: ExecutionContext)
  extends Controller with Secured {
  // TODO: check type values between borrow and lend (improvement)

  implicit val exchangePOSTDTOReads: Reads[ExchangePOSTDTO] = (
    (JsPath \ "name").read[String](notEqual(Const.errorMessageEmptyStringJSON, "")) and
      (JsPath \ "date").readNullable[DateDTO] and
      (JsPath \ "type").read[String](notEqual(Const.errorMessageEmptyStringJSON, "")) and
      (JsPath \ "amount").read[Double]
    ) (ExchangePOSTDTO.apply _)

  implicit val exchangeGETDTOWrites: Writes[ExchangeGETDTO] = (
    (JsPath \ "name").write[String] and
      (JsPath \ "date").write[DateDTO] and
      (JsPath \ "type").write[String] and
      (JsPath \ "amount").write[Double]
    ) (unlift(ExchangeGETDTO.unapply))

  implicit val exchangeAllGETDTOWrites: Writes[ExchangeAllGETDTO] = (
    (JsPath \ "id").write[Int] and
      (JsPath \ "name").write[String] and
      (JsPath \ "date").write[DateDTO] and
      (JsPath \ "type").write[String] and
      (JsPath \ "amount").write[Double]
    ) (unlift(ExchangeAllGETDTO.unapply))

  implicit val exchangePATCHDTOReads: Reads[ExchangePUTDTO] = (
    (JsPath \ "name").readNullable[String] and
      (JsPath \ "date").readNullable[DateDTO] and
      (JsPath \ "type").readNullable[String] and
      (JsPath \ "amount").readNullable[Double]
    ) (ExchangePUTDTO.apply _)

  def create(): Action[JsValue] = Authenticated.async(BodyParsers.parse.json) { implicit request =>
    val result = request.body.validate[ExchangePOSTDTO]

    result.fold(
      errors => Future.successful {
        BadRequest(Json.obj("status" -> "ERROR", "message" -> JsError.toJson(errors)))
      },
      exchange => {
        // we look for the user email in the JWT
        exchangeDAO.insert(request.jwtSession.getAs[String](Const.ValueStoredJWT).get, exchange).map { _ =>
          Created(Json.obj("status" -> "OK", "message" -> "exchange '%s' created".format(exchange.name)))
        }
      }
    )
  }

  def readAll: Action[JsValue] = Authenticated.async(BodyParsers.parse.json) { implicit request =>
    val result = request.body.validate[FromToDatesDTO]

    result.fold(
      errors => Future.successful {
        BadRequest(Json.obj("status" -> "ERROR", "message" -> JsError.toJson(errors)))
      },
      dates => {
        // we look for the user email in the JWT
        exchangeDAO.findAll(request.jwtSession.getAs[String](Const.ValueStoredJWT).get, dates).map { exchanges =>
          Ok(Json.toJson(exchanges))
        }
      }
    )
  }

  def read(id: Int): Action[AnyContent] = Authenticated.async { implicit request =>
    // we look for the user email in the JWT
    exchangeDAO.find(request.jwtSession.getAs[String](Const.ValueStoredJWT).get, id).map { exchange =>
      Ok(Json.toJson(exchange))
    }.recover {
      // case in not found the specified exchange with its id
      case _: NoSuchElementException =>
        NotFound(Json.obj("status" -> "ERROR", "message" -> "exchange with id '%s' not found".format(id)))
    }
  }

  def update(id: Int): Action[JsValue] = Authenticated.async(BodyParsers.parse.json) { implicit request =>
    val result = request.body.validate[ExchangePUTDTO]

    result.fold(
      errors => Future.successful {
        BadRequest(Json.obj("status" -> "ERROR", "message" -> JsError.toJson(errors)))
      },
      exchange => {
        // we look for the user email in the JWT
        exchangeDAO.update(request.jwtSession.getAs[String](Const.ValueStoredJWT).get, id, exchange).map { _ =>
          Ok(Json.obj("status" -> "OK", "message" -> "exchange updated"))
        }.recover {
          // case in not found the specified exchange with its id
          case _: NoSuchElementException =>
            NotFound(Json.obj("status" -> "ERROR", "message" -> "exchange with id '%s' not found".format(id)))
        }
      }
    )
  }

  def delete(id: Int): Action[AnyContent] = Authenticated.async { implicit request =>
    // we look for the user email in the JWT
    exchangeDAO.delete(request.jwtSession.getAs[String](Const.ValueStoredJWT).get, id).map { _ =>
      Ok(Json.obj("status" -> "OK", "user" -> "exchange deleted"))
    }.recover {
      // case in not found the specified exchange with its id
      case _: NoSuchElementException =>
        NotFound(Json.obj("status" -> "ERROR", "message" -> "exchange with id '%s' not found".format(id)))
    }
  }
}
