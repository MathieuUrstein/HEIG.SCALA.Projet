package dao

import javax.inject.Inject

import models.{User, UserGETDTO, UserPATCHDTO}
import org.mindrot.jbcrypt.BCrypt
import play.api.db.slick.DatabaseConfigProvider
import play.db.NamedDatabase
import slick.backend.DatabaseConfig
import slick.driver.SQLiteDriver.api._
import slick.driver.{JdbcProfile, SQLiteDriver}
import slick.lifted.{Index, MappedProjection, ProvenShape}
import utils.Const

import scala.concurrent.{ExecutionContext, Future}

class UserDAO @Inject()(@NamedDatabase(Const.DbName) dbConfigProvider: DatabaseConfigProvider)
                       (implicit executionContext: ExecutionContext) {
  private val dbConfig: DatabaseConfig[JdbcProfile] = dbConfigProvider.get[JdbcProfile]

  val users: TableQuery[UserTable] = TableQuery[UserTable]

  def insert(user: User): Future[Unit] = {
    // hash the password before store it
    val passwordHash = BCrypt.hashpw(user.password, BCrypt.gensalt())
    user.password = passwordHash

    dbConfig.db.run(users += user).map { _ => () }
  }

  def getId(email: String): Future[Int] = {
    dbConfig.db.run(users.filter(_.email === email).map(_.userId).result.head)
  }

  def getPassword(email: String): Future[String] = {
    dbConfig.db.run(users.filter(_.email === email).map(_.userPassword).result.head)
  }

  def find(email: String): Future[UserGETDTO] = {
    dbConfig.db.run(users.filter(_.email === email).map(_.userInfo).result.head)
  }

  private def updateRequest(email: String, field: UserTable => SQLiteDriver.api.Rep[String], value: String) = {
    dbConfig.db.run(users.filter(_.email === email).map(field).update(value)).map { _ => () }
  }

  def update(email: String, user: UserPATCHDTO): Future[Unit] = {
    // default future with success and do nothing
    var futureToReturn = Future.successful(())

    // we update only the present fields (not None value)
    if (user.fullname.isDefined) {
      futureToReturn = updateRequest(email, _.fullname, user.fullname.get)
    }

    if (user.password.isDefined) {
      // hash the password before store it
      val passwordHash = BCrypt.hashpw(user.password.get, BCrypt.gensalt())
      user.password = Option(passwordHash)

      futureToReturn = updateRequest(email, _.password, user.password.get)
    }

    if (user.currency.isDefined) {
      futureToReturn = updateRequest(email, _.currency, user.currency.get)
    }

    if (user.email.isDefined) {
      futureToReturn = updateRequest(email, _.email, user.email.get)
    }

    futureToReturn
  }

  def delete(email: String): Future[Unit] = {
    dbConfig.db.run(users.filter(_.email === email).delete).map { _ => () }
  }

  class UserTable(tag: Tag) extends Table[User](tag, "user") {
    def id: Rep[Int] = column[Int]("id", O.PrimaryKey, O.AutoInc)
    def fullname: Rep[String] = column[String]("fullname")
    def email: Rep[String] = column[String]("email")
    // it is the hash of the password
    def password: Rep[String] = column[String]("password")
    def currency: Rep[String] = column[String]("currency")

    // make email unique for each user
    def emailIndex: Index = index("unique_email", email, unique = true)

    def * : ProvenShape[User] = (fullname, email, password, currency) <> ((User.apply _).tupled, User.unapply)

    def userInfo: MappedProjection[UserGETDTO, (String, String, String)] = {
      (fullname, email, currency) <> (UserGETDTO.tupled, UserGETDTO.unapply)
    }
    def userId: Rep[Int] = id
    def userPassword: Rep[String] = password
  }
}
