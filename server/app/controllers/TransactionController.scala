package controllers

import javax.inject.Inject

import dao.TransactionDAO
import models._
import pdi.jwt.JwtSession._
import play.api.libs.functional.syntax._
import play.api.libs.json._
import play.api.mvc._
import utils.Const

import scala.concurrent.{ExecutionContext, Future}

class TransactionController @Inject()(transactionDAO: TransactionDAO)(implicit executionContext: ExecutionContext)
  extends Controller with Secured {
  implicit val transactionBudgetGETDTOWrites: Writes[TransactionBudgetGETDTO] = (
    (JsPath \ "id").write[Int] and
      (JsPath \ "name").write[String]
    ) (unlift(TransactionBudgetGETDTO.unapply))

  implicit val transactionPOSTDTOReads: Reads[TransactionPOSTDTO] = (
    (JsPath \ "name").read[String](notEqual(Const.errorMessageEmptyStringJSON, "")) and
      (JsPath \ "date").readNullable[DateDTO] and
      (JsPath \ "budgetId").read[Int] and
      (JsPath \ "amount").read[Double]
    ) (TransactionPOSTDTO.apply _)

  implicit val transactionGETDTOWrites: Writes[TransactionGETDTO] = (
    (JsPath \ "name").write[String] and
      (JsPath \ "date").write[DateDTO] and
      (JsPath \ "budget").write[TransactionBudgetGETDTO] and
      (JsPath \ "amount").write[Double]
    ) (unlift(TransactionGETDTO.unapply))

  implicit val transactionAllGETDTOWrites: Writes[TransactionAllGETDTO] = (
    (JsPath \ "id").write[Int] and
      (JsPath \ "name").write[String] and
      (JsPath \ "date").write[DateDTO] and
      (JsPath \ "budget").write[TransactionBudgetGETDTO] and
      (JsPath \ "amount").write[Double]
    ) (unlift(TransactionAllGETDTO.unapply))

  implicit val transactionPATCHDTOReads: Reads[TransactionPATCHDTO] = (
    (JsPath \ "name").readNullable[String] and
      (JsPath \ "date").readNullable[DateDTO] and
      (JsPath \ "budgetId").readNullable[Int] and
      (JsPath \ "amount").readNullable[Double]
    ) (TransactionPATCHDTO.apply _)

  def create(): Action[JsValue] = Authenticated.async(BodyParsers.parse.json) { implicit request =>
    val result = request.body.validate[TransactionPOSTDTO]

    result.fold(
      errors => Future.successful {
        BadRequest(Json.obj("status" -> "ERROR", "message" -> JsError.toJson(errors)))
      }
      ,
      transaction => {
        // we look for the user email in the JWT
        transactionDAO.insert(request.jwtSession.getAs[String](Const.ValueStoredJWT).get, transaction).map { _ =>
          Created(Json.obj("status" -> "OK", "message" -> "transaction '%s' created".format(transaction.name)))
        }.recover {
          // case in not found the specified budgetId value
          case _: NoSuchElementException =>
            NotFound(Json.obj("status" -> "ERROR", "message" -> "budget with id '%s' not found".format(transaction.budgetId)))
          // case in problem with insertion
          case e: Exception => BadRequest(Json.obj("status" -> "ERROR", "message" -> e.getMessage))
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
        transactionDAO.findAll(request.jwtSession.getAs[String](Const.ValueStoredJWT).get, dates).map { transactions =>
          Ok(Json.toJson(transactions))
        }
      }
    )
  }

  def read(id: Int): Action[AnyContent] = Authenticated.async { implicit request =>
    // we look for the user email in the JWT
    transactionDAO.find(request.jwtSession.getAs[String](Const.ValueStoredJWT).get, id).map { transaction =>
      Ok(Json.toJson(transaction))
    }.recover {
      // case in not found the specified transaction with its id (or the transaction doesn't belong to this user)
      case _: NoSuchElementException =>
        NotFound(Json.obj("status" -> "ERROR", "message" -> "transaction with id '%s' not found".format(id)))
    }
  }

  def update(id: Int): Action[JsValue] = Authenticated.async(BodyParsers.parse.json) { implicit request =>
    val result = request.body.validate[TransactionPATCHDTO]

    result.fold(
      errors => Future.successful {
        BadRequest(Json.obj("status" -> "ERROR", "message" -> JsError.toJson(errors)))
      },
      transaction => {
        transactionDAO.update(request.jwtSession.getAs[String](Const.ValueStoredJWT).get, id, transaction).map { _ =>
          Ok(Json.obj("status" -> "OK", "message" -> "transaction updated"))
        }.recover {
          // case in not found the specified transaction with its id (or the transaction doesn't belong to this user)
          case _: NoSuchElementException =>
            NotFound(Json.obj("status" -> "ERROR", "message" -> "transaction with id '%s' not found".format(id)))
          // case in problem with the update of the new budget
          case e: Exception => BadRequest(Json.obj("status" -> "ERROR", "message" -> e.getMessage))
        }
      }
    )
  }

  def delete(id: Int): Action[AnyContent] = Authenticated.async { implicit request =>
    // we look for the user email in the JWT
    transactionDAO.delete(request.jwtSession.getAs[String](Const.ValueStoredJWT).get, id).map { _ =>
      Ok(Json.obj("status" -> "OK", "user" -> "transaction deleted"))
    }.recover {
      // case in not found the specified transaction with its id (or the transaction doesn't belong to this user)
      case _: NoSuchElementException =>
        NotFound(Json.obj("status" -> "ERROR", "message" -> "transaction with id '%s' not found".format(id)))
    }
  }
}
