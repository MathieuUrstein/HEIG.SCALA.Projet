package models

case class User(lastName: String, firstName: String, email: String, password: String, currency: String)
case class UserDTO(lastName: String, firstName: String, email: String, currency: String)
case class Password(password: String)
case class PasswordDTO(oldPassword: String, newPassword: String)
