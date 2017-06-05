package controllers

import java.sql.Date
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

  implicit val transactionPATCHDTOReads: Reads[TransactionPATCHDTO] = (
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

  def readAll: Action[JsValue] = Authenticated.async(BodyParsers.parse.json) { implicit request =>
    val result = request.body.validate[FromToDatesDTO]

    result.fold(
      errors => Future.successful {
        BadRequest(Json.obj("status" -> "ERROR", "message" -> JsError.toJson(errors)))
      },
      dates => {
        // we look for the user email in the JWT
        transactionDAO.findAll(request.jwtSession.getAs[String](Const.ValueStoredJWT).get).map { transactions =>
          // if from and to dates are presents (JSON), we keep only the corresponding transactions
          val transactionsToSendToKeep = transactions.filter{ t =>
            if (dates.from.isDefined) {
              val dateFrom = Date.valueOf(dates.from.get.year + "-" + dates.from.get.month + "-" + dates.from.get.day)

              t.date.equals(dateFrom) || t.date.after(dateFrom)
            }
            else {
              true
            }
          }.filter { t =>
            if (dates.to.isDefined) {
              val dateTo = Date.valueOf(dates.to.get.year + "-" + dates.to.get.month + "-" + dates.to.get.day)

              t.date.equals(dateTo) || t.date.before(dateTo)
            }
            else {
              true
            }
          }

          val transactionsToSend = transactionsToSendToKeep.map { t =>
            val dateToSend = Option(DateDTO(t.date.toString.substring(8, 10).toInt, t.date.toString.substring(5, 7).toInt,
              t.date.toString.substring(0, 4).toInt))
            val transactionToSend = TransactionAllGETDTO(t.id, t.name, dateToSend, t.amount)

            transactionToSend
          }

          Ok(Json.obj("status" -> "OK", "transactions" -> transactionsToSend))
        }
      }
    )
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
