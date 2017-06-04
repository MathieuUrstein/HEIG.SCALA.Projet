package models

case class User(fullname: String, email: String, var password: String, currency: String)
case class UserGETDTO(fullname: String, email: String, currency: String)
case class UserPATCHDTO(fullname: String, email: String, var password: String, currency: String)
case class LoginForm(email: String, password: String)
