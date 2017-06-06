package controllers

import javax.inject.Inject

import dao.BudgetDAO
import models.{BudgetAndTakesFromAllGETDTO, BudgetAndTakesFromGETDTO, BudgetPOSTDTO, TakesFromDTO}
import org.sqlite.SQLiteException
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

  // TODO: control type values between income and outcome (improvement)

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

  /*implicit val exchangePATCHDTOReads: Reads[ExchangePATCHDTO] = (
    (JsPath \ "name").readNullable[String] and
      (JsPath \ "date").readNullable[DateDTO] and
      (JsPath \ "type").readNullable[String] and
      (JsPath \ "amount").readNullable[Double]
    ) (ExchangePATCHDTO.apply _)*/

  def create(): Action[JsValue] = Authenticated.async(BodyParsers.parse.json) { implicit request =>
    val result = request.body.validate[BudgetPOSTDTO]

    result.fold(
      errors => Future.successful {
        BadRequest(Json.obj("status" -> "ERROR", "message" -> JsError.toJson(errors)))
      },
      budget => {
        // we look for the user email in the JWT
        val userEmail = request.jwtSession.getAs[String](Const.ValueStoredJWT).get

        // test if takesFrom is defined or not (if we must or not add entries in table takes_from)
        if (budget.takesFrom.isDefined) {
          // it is an error to define a takesFrom tab with the type income for the budget to create
          if (budget.`type`.equals("income")) {
            Future.successful {
              BadRequest(Json.obj("status" -> "ERROR", "message" ->
                "takesFrom hast to be define only for outcome budgets"))
            }
          }
          else {
            // insert the budget
            budgetDAO.insertBudget(userEmail, budget).map { f =>
              f.map { insertedBudgetId =>
                // verify that the specified budgets (id) in takesFrom field exist among the budgets of this user
                budget.takesFrom.get.foreach { b =>
                  budgetDAO.isBudgetExisting(userEmail, b.budgetId).map { r =>
                    r.map { v =>
                      // TODO: show an error for the client of the api for not existing budgets specified in takesFrom values and to not respect conditions (improvement)

                      // the insertion in takes_from table is made only if the specified budget exists (field takesFrom)
                      // and if the conditions are respected
                      if (v) {
                        // FIXME: this message returned

                        budgetDAO.insertTakesFrom(insertedBudgetId, b).map { _ => }.recover {
                          case e: Exception => println(e.getMessage)
                          case e: SQLiteException if e.getResultCode.code == Const.SQLiteUniqueConstraintErrorCode =>
                            println(e.getMessage)
                        }
                      }
                    }
                  }
                }
              }

              // FIXME: this message returned

              Created(Json.obj("status" -> "OK", "message" ->
                "budget '%s' created with existing and conditions respectful budgets in takesFrom values"
                  .format(budget.name)))
            }
          }
        }
        else {
          budgetDAO.insertBudget(userEmail, budget).map { _ =>
            Created(Json.obj("status" -> "OK", "message" -> "budget '%s' created".format(budget.name)))
          }
        }
      }
    )
  }

  def readAll: Action[AnyContent] = Authenticated.async { implicit request =>
    // we look for the user email in the JWT
    budgetDAO.findAll(request.jwtSession.getAs[String](Const.ValueStoredJWT).get).map { budgets =>
      Ok(Json.obj("status" -> "OK", "budgets" -> budgets))
    }
  }

  def read(id: Int): Action[AnyContent] = Authenticated.async { implicit request =>
    // we look for the user email in the JWT
    budgetDAO.find(request.jwtSession.getAs[String](Const.ValueStoredJWT).get, id).map { budget =>
      Ok(Json.obj("status" -> "OK", "budget" -> budget))
    }.recover {
      // case in not found the specified budget with its id
      case _: NoSuchElementException =>
        NotFound(Json.obj("status" -> "ERROR", "message" -> "budget with id '%s' not found".format(id)))
    }
  }

  /*def update(id: Int): Action[JsValue] = Authenticated.async(BodyParsers.parse.json) { implicit request =>
    val result = request.body.validate[ExchangePATCHDTO]

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
  }*/

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
