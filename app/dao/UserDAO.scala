package dao

import java.util
import java.util.NoSuchElementException
import javax.inject.Inject

import models.{Password, User, UserDTO}
import play.api.db.slick.DatabaseConfigProvider
import play.db.NamedDatabase
import slick.backend.DatabaseConfig
import slick.driver.JdbcProfile
import slick.driver.SQLiteDriver.api._
import slick.lifted.{Index, MappedProjection, ProvenShape}
import utils.Const

import scala.concurrent.{ExecutionContext, Future}

class UserDAO @Inject()(@NamedDatabase(Const.DbName) dbConfigProvider: DatabaseConfigProvider)(implicit executionContext: ExecutionContext) {
  private val dbConfig: DatabaseConfig[JdbcProfile] = dbConfigProvider.get[JdbcProfile]

  private val users: TableQuery[UserTable] = TableQuery[UserTable]

  def all(): Future[Seq[UserDTO]] = dbConfig.db.run(users.map(_.userInfo).result)

  def insert(user: User): Future[Unit] = dbConfig.db.run(users += user).map { _ => () }

  def find(id: Int): Future[UserDTO] = dbConfig.db.run(users.filter(_.id === id).map(_.userInfo).result.head)

  def fullUpdate(id: Int, user: UserDTO): Future[Unit] = {
    dbConfig.db.run(users.filter(_.id === id).map(u => (u.lastName, u.firstName, u.email, u.currency))
      .update(user.lastName, user.firstName, user.email, user.currency)).map {
      case 0 => throw new NoSuchElementException
      case _ => Unit
    }
  }

  def getPassword(id: Int): Future[Password] = dbConfig.db.run(users.filter(_.id === id).map(_.userPassword).result.head)

  def updatePassword(id: Int, password: String): Future[Unit] = {
    dbConfig.db.run(users.filter(_.id === id).map(_.password).update(password)).map { _ => () }
  }

  def delete(id: Int): Future[Unit] = {
    dbConfig.db.run(users.filter(_.id === id).delete).map {
      case 0 => throw new NoSuchElementException
      case _ => Unit
    }
  }

  private class UserTable(tag: Tag) extends Table[User](tag, "user") {
    def id: Rep[Int] = column[Int]("id", O.PrimaryKey, O.AutoInc)
    def lastName: Rep[String] = column[String]("lastName")
    def firstName: Rep[String] = column[String]("firstName")
    def email: Rep[String] = column[String]("email")
    def password: Rep[String] = column[String]("password")
    def currency: Rep[String] = column[String]("currency")

    def * : ProvenShape[User] = (lastName, firstName, email, password, currency) <> (User.tupled, User.unapply)
    def userInfo: MappedProjection[UserDTO, (String, String, String, String)] = {
      (lastName, firstName, email, currency) <> (UserDTO.tupled, UserDTO.unapply)
    }
    def userPassword: MappedProjection[Password, String] = password <> (Password, Password.unapply)
    // make email unique for each user
    def emailIndex: Index = index("unique_email", email, unique = true)
  }
}
