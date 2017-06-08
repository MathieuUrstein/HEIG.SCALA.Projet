package controllers

import javax.inject.Inject

import dao.BudgetDAO
import models._
import pdi.jwt.JwtSession._
import play.api.libs.functional.syntax._
import play.api.libs.json._
import play.api.mvc._
import utils.Const

import scala.concurrent.{ExecutionContext, Future}

class BudgetController @Inject()(budgetDAO: BudgetDAO)(implicit executionContext: ExecutionContext)
  extends Controller with Secured {
  implicit val takesFromDTOReads: Reads[TakesFromDTO] = (
    (JsPath \ "order").read[Int] and
      (JsPath \ "budgetId").read[Int]
    ) (TakesFromDTO.apply _)

  implicit val takesFromDTOWrites: Writes[TakesFromDTO] = (
    (JsPath \ "order").write[Int] and
      (JsPath \ "budgetId").write[Int]
    ) (unlift(TakesFromDTO.unapply))

  // TODO: check type values between income and outcome (improvement)

  implicit val budgetPOSTDTOReads: Reads[BudgetPOSTDTO] = (
    (JsPath \ "name").read[String](notEqual(Const.errorMessageEmptyStringJSON, "")) and
      (JsPath \ "type").read[String](notEqual(Const.errorMessageEmptyStringJSON, "")) and
      (JsPath \ "used").read[Double] and
      (JsPath \ "left").read[Double] and
      (JsPath \ "exceeding").read[Double] and
      (JsPath \ "persistent").read[Int] and
      (JsPath \ "reported").read[Boolean] and
      (JsPath \ "color").read[String](notEqual(Const.errorMessageEmptyStringJSON, "")) and
      (JsPath \ "takesFrom").readNullable[Seq[TakesFromDTO]](notEqual(Const.errorMessageEmptyArrayJSON, Seq.empty))
    ) (BudgetPOSTDTO.apply _)

  implicit val budgetAndTakesFromAllGETDTOWrites: Writes[BudgetAndTakesFromAllGETDTO] = (
    (JsPath \ "id").write[Int] and
      (JsPath \ "name").write[String] and
      (JsPath \ "type").write[String] and
      (JsPath \ "used").write[Double] and
      (JsPath \ "left").write[Double] and
      (JsPath \ "exceeding").write[Double] and
      (JsPath \ "persistent").write[Int] and
      (JsPath \ "reported").write[Boolean] and
      (JsPath \ "color").write[String] and
      (JsPath \ "takesFrom").writeNullable[Seq[TakesFromDTO]]
    ) (unlift(BudgetAndTakesFromAllGETDTO.unapply))

  implicit val budgetAndTakesFromGETDTOWrites: Writes[BudgetAndTakesFromGETDTO] = (
    (JsPath \ "name").write[String] and
      (JsPath \ "type").write[String] and
      (JsPath \ "used").write[Double] and
      (JsPath \ "left").write[Double] and
      (JsPath \ "exceeding").write[Double] and
      (JsPath \ "persistent").write[Int] and
      (JsPath \ "reported").write[Boolean] and
      (JsPath \ "color").write[String] and
      (JsPath \ "takesFrom").writeNullable[Seq[TakesFromDTO]]
    ) (unlift(BudgetAndTakesFromGETDTO.unapply))

  // TODO: patch (update) type and takesFrom values (improvement)

  implicit val budgetPATCHDTOReads: Reads[BudgetPATCHDTO] = (
    (JsPath \ "name").readNullable[String] and
      (JsPath \ "used").readNullable[Double] and
      (JsPath \ "left").readNullable[Double] and
      (JsPath \ "exceeding").readNullable[Double] and
      (JsPath \ "persistent").readNullable[Int] and
      (JsPath \ "reported").readNullable[Boolean] and
      (JsPath \ "color").readNullable[String] and
      (JsPath \ "takesFrom").readNullable[Seq[TakesFromDTO]]
    ) (BudgetPATCHDTO.apply _)

  // TODO: check for no negative values for left and used fields (improvement)
  // TODO: check that the order for the takesFrom values are correct (improvement)

  def create(): Action[JsValue] = Authenticated.async(BodyParsers.parse.json) { implicit request =>
    val result = request.body.validate[BudgetPOSTDTO]

    result.fold(
      errors => Future.successful {
        BadRequest(Json.obj("status" -> "ERROR", "message" -> JsError.toJson(errors)))
      },
      budget => {
        // we look for the user email in the JWT
        budgetDAO.insert(request.jwtSession.getAs[String](Const.ValueStoredJWT).get, budget).map { _ =>
          Created(Json.obj("status" -> "OK", "message" -> "budget '%s' created with the eventual takesFrom values".format(budget.name)))
        }.recover {
          case e: Exception => BadRequest(Json.obj("status" -> "ERROR", "message" -> e.getMessage))
        }
      }
    )
  }

  def readAll: Action[AnyContent] = Authenticated.async { implicit request =>
    // we look for the user email in the JWT
    budgetDAO.findAll(request.jwtSession.getAs[String](Const.ValueStoredJWT).get).map { budgets =>
      Ok(Json.toJson(budgets))
    }
  }

  def read(id: Int): Action[AnyContent] = Authenticated.async { implicit request =>
    // we look for the user email in the JWT
    budgetDAO.find(request.jwtSession.getAs[String](Const.ValueStoredJWT).get, id).map { budget =>
      Ok(Json.toJson(budget))
    }.recover {
      // case in not found the specified budget with its id
      case _: NoSuchElementException =>
        NotFound(Json.obj("status" -> "ERROR", "message" -> "budget with id '%s' not found".format(id)))
    }
  }

  def update(id: Int): Action[JsValue] = Authenticated.async(BodyParsers.parse.json) { implicit request =>
    val result = request.body.validate[BudgetPATCHDTO]

    result.fold(
      errors => Future.successful {
        BadRequest(Json.obj("status" -> "ERROR", "message" -> JsError.toJson(errors)))
      },
      budget => {
        // we look for the user email in the JWT
        budgetDAO.update(request.jwtSession.getAs[String](Const.ValueStoredJWT).get, id, budget).map { _ =>
          Ok(Json.obj("status" -> "OK", "message" -> "budget updated"))
        }.recover {
          // case in not found the specified budget with its id
          case _: NoSuchElementException =>
            NotFound(Json.obj("status" -> "ERROR", "message" -> "budget with id '%s' not found".format(id)))
        }
      }
    )
  }

  def delete(id: Int): Action[AnyContent] = Authenticated.async { implicit request =>
    // we look for the user email in the JWT
    budgetDAO.delete(request.jwtSession.getAs[String](Const.ValueStoredJWT).get, id).map { _ =>
      Ok(Json.obj("status" -> "OK", "user" -> "budget deleted"))
    }.recover {
      // case in not found the specified budget with its id
      case _: NoSuchElementException =>
        NotFound(Json.obj("status" -> "ERROR", "message" -> "budget with id '%s' not found".format(id)))
    }
  }
}
