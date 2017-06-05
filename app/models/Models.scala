package models

import java.sql.Date

case class User(fullname: String, email: String, var password: String, currency: String)
case class UserGETDTO(fullname: String, email: String, currency: String)
case class UserPATCHDTO(fullname: String, email: String, var password: String, currency: String)
case class LoginFormDTO(email: String, password: String)

case class DateDTO(day: Int, month: Int, year: Int)
case class FromToDatesDTO(from: Option[DateDTO], to: Option[DateDTO])

case class Transaction(name: String, date: Date, amount: Double, userId: Int)
case class TransactionPOSTDTO(name: String, date: Option[DateDTO], amount: Double)
// TODO : ajouter budget
case class TransactionGET(id: Int, name: String, date: Date, amount: Double)
case class TransactionAllGETDTO(id: Int, name: String, date: Option[DateDTO], amount: Double)
case class TransactionGETDTO(name: String, date: Option[DateDTO], amount: Double)
case class TransactionPATCHDTO(name: Option[String], date: Option[DateDTO], amount: Option[Double])

case class Exchange(name: String, date: Date, `type`: String, amount: Double, userId: Int)
case class ExchangePOSTDTO(name: String, date: Option[DateDTO], `type`: String, amount: Double)
case class ExchangeGET(id: Int, name: String, date: Date, `type`: String, amount: Double)
case class ExchangeAllGETDTO(id: Int, name: String, date: Option[DateDTO], `type`: String, amount: Double)
case class ExchangeGETDTO(name: String, date: Option[DateDTO], `type`: String, amount: Double)
case class ExchangePATCHDTO(name: Option[String], date: Option[DateDTO], `type`: Option[String], amount: Option[Double])

