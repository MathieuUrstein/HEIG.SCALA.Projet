package dao

import javax.inject.Inject

import models.{User, UserGETDTO, UserPATCHDTO}
import org.mindrot.jbcrypt.BCrypt
import play.api.db.slick.DatabaseConfigProvider
import play.db.NamedDatabase
import slick.backend.DatabaseConfig
import slick.driver.JdbcProfile
import slick.driver.SQLiteDriver.api._
import slick.lifted.{Index, MappedProjection, ProvenShape}
import uk.gov.hmrc.emailaddress._
import utils.Const

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}

class UserDAO @Inject()(@NamedDatabase(Const.DbName) dbConfigProvider: DatabaseConfigProvider)
                       (implicit executionContext: ExecutionContext) {
  private val dbConfig: DatabaseConfig[JdbcProfile] = dbConfigProvider.get[JdbcProfile]
  // initialisation of foreign key in SQLite
  dbConfig.db.run(DBIO.seq(sqlu"PRAGMA foreign_keys = ON;")).map { _ => () }

  val users: TableQuery[UserTable] = TableQuery[UserTable]

  def insert(user: User): Future[Unit] = {
    // check the validity of the email
    if (!EmailAddress.isValid(user.email)) {
      return Future.failed(new Exception("invalid email"))
    }

    // hash the password before store it
    val passwordHash = BCrypt.hashpw(user.password, BCrypt.gensalt())
    user.password = passwordHash

    dbConfig.db.run(users += user).map { _ => () }
  }

  def getId(email: String): Future[Int] = {
    dbConfig.db.run(users.filter(_.email === email).map(_.id).result.head)
  }

  def getPassword(email: String): Future[String] = {
    dbConfig.db.run(users.filter(_.email === email).map(_.password).result.head)
  }

  def find(email: String): Future[UserGETDTO] = {
    dbConfig.db.run(users.filter(_.email === email).map(_.userInfo).result.head)
  }

  private def updateRequest(email: String, field: UserTable => Rep[String], value: String) = {
    dbConfig.db.run(users.filter(_.email === email).map(field).update(value)).map { _ => () }
  }

  def update(email: String, user: UserPATCHDTO): Future[Unit] = {
    // we first verify that this user exists
    dbConfig.db.run(users.filter(_.email === email).result.head).map { _ =>
      // we update only the present fields (not None value)
      if (user.fullname.isDefined) {
        updateRequest(email, _.fullname, user.fullname.get)
      }

      if (user.password.isDefined) {
        // hash the password before store it
        val passwordHash = BCrypt.hashpw(user.password.get, BCrypt.gensalt())
        user.password = Option(passwordHash)

        updateRequest(email, _.password, user.password.get)
      }

      if (user.currency.isDefined) {
        updateRequest(email, _.currency, user.currency.get)
      }

      if (user.email.isDefined) {
        // check the validity of the email
        if (!EmailAddress.isValid(user.email.get)) {
          return Future.failed(new Exception("invalid email, email not updated"))
        }

        // wait for an potential error with the new email
        Await.result(updateRequest(email, _.email, user.email.get), Duration(Const.maxTimeToWaitInSeconds,
          Const.timeToWaitUnit))
      }
    }
  }

  def delete(email: String): Future[Unit] = {
    dbConfig.db.run(users.filter(_.email === email).delete).map {
      case 0 => throw new NoSuchElementException
      case _ => Unit
    }
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
  }
}
