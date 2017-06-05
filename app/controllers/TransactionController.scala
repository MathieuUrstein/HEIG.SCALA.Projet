package controllers

import javax.inject.Inject

import dao.TransactionDAO
import models._
import org.sqlite.SQLiteException
import pdi.jwt.JwtSession._
import play.api.libs.functional.syntax._
import play.api.libs.json._
import play.api.mvc._
import utils.Const

import scala.concurrent.{ExecutionContext, Future}

class TransactionController @Inject()(transactionDAO: TransactionDAO)(implicit executionContext: ExecutionContext)
  extends Controller with Secured {
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

  implicit val transactionPOSTDTOReads: Reads[TransactionPOSTDTO] = (
    (JsPath \ "name").read[String](notEqual("")) and
      (JsPath \ "date").readNullable[DateDTO] and
      (JsPath \ "amount").read[Double]
    ) (TransactionPOSTDTO.apply _)

  implicit val transactionGETDTOWrites: Writes[TransactionGETDTO] = (
    (JsPath \ "name").write[String] and
      (JsPath \ "date").writeNullable[DateDTO] and
      (JsPath \ "amount").write[Double]
    ) (unlift(TransactionGETDTO.unapply))

  implicit val transactionAllGETDTOWrites: Writes[TransactionAllGETDTO] = (
    (JsPath \ "id").write[Int] and
      (JsPath \ "name").write[String] and
      (JsPath \ "date").writeNullable[DateDTO] and
      (JsPath \ "amount").write[Double]
    ) (unlift(TransactionAllGETDTO.unapply))

  implicit val TransactionPATCHDTOReads: Reads[TransactionPATCHDTO] = (
    (JsPath \ "name").readNullable[String] and
      (JsPath \ "date").readNullable[DateDTO] and
      (JsPath \ "amount").readNullable[Double]
    ) (TransactionPATCHDTO.apply _)

  def create(): Action[JsValue] = Authenticated.async(BodyParsers.parse.json) { implicit request =>
    val result = request.body.validate[TransactionPOSTDTO]

    result.fold(
      errors => Future.successful {
        BadRequest(Json.obj("status" -> "ERROR", "message" -> JsError.toJson(errors)))
      },
      transaction => {
        transactionDAO.insert(request.jwtSession.getAs[String](Const.ValueStoredJWT).get, transaction).map { _ =>
          Created(Json.obj("status" -> "OK", "message" -> "transaction '%s' created".format(transaction.name)))
        }
      }
    )
  }

  // TODO : ajouter from et do traitement dans le body (dates des transactions)

  def readAll: Action[AnyContent] = Authenticated.async { implicit request =>
    // we look for the user email in the JWT
    transactionDAO.findAll(request.jwtSession.getAs[String](Const.ValueStoredJWT).get).map { transactions =>
      val transactionsToSend = transactions.map { t =>
        val dateToSend = Option(DateDTO(t.date.toString.substring(8, 10).toInt, t.date.toString.substring(5, 7).toInt,
          t.date.toString.substring(0, 4).toInt))
        val transactionToSend = TransactionAllGETDTO(t.id, t.name, dateToSend, t.amount)

        transactionToSend
      }

      Ok(Json.obj("status" -> "OK", "transactions" -> transactionsToSend))
    }
  }

  def read(id: Int): Action[AnyContent] = Authenticated.async {
    transactionDAO.find(id).map { transaction =>
      val dateToSend = Option(DateDTO(transaction.date.toString.substring(8, 10).toInt,
        transaction.date.toString.substring(5, 7).toInt, transaction.date.toString.substring(0, 4).toInt))
      val transactionToSend = TransactionGETDTO(transaction.name, dateToSend, transaction.amount)

      Ok(Json.obj("status" -> "OK", "transaction" -> transactionToSend))
    }.recover {
      // case in not found the specified transaction with its id
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
        transactionDAO.update(id, transaction).map { _ =>
          Ok(Json.obj("status" -> "OK", "message" -> "transaction updated"))
        }.recover {
          // case in not found the specified transaction with its id
          case _: NoSuchElementException =>
            NotFound(Json.obj("status" -> "ERROR", "message" -> "transaction with id '%s' not found".format(id)))
        }
      }
    )
  }

  def delete(id: Int): Action[AnyContent] = Authenticated.async {
    transactionDAO.delete(id).map { _ =>
      Ok(Json.obj("status" -> "OK", "user" -> "transaction deleted"))
    }.recover {
      // case in not found the specified transaction with its id
      case _: NoSuchElementException =>
        NotFound(Json.obj("status" -> "ERROR", "message" -> "transaction with id '%s' not found".format(id)))
    }
  }
}
