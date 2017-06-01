package dao

import javax.inject.Inject

import models.User
import play.api.db.slick.DatabaseConfigProvider
import play.db.NamedDatabase
import slick.backend.DatabaseConfig
import slick.driver.JdbcProfile
import slick.driver.SQLiteDriver.api._
import slick.lifted.{Index, ProvenShape}

import scala.concurrent.{ExecutionContext, Future}

class UserDAO @Inject()(@NamedDatabase("piggybank") dbConfigProvider: DatabaseConfigProvider)(implicit executionContext: ExecutionContext) {
  private val dbConfig: DatabaseConfig[JdbcProfile] = dbConfigProvider.get[JdbcProfile]

  private val users: TableQuery[UserTable] = TableQuery[UserTable]

  def all(): Future[Seq[User]] = dbConfig.db.run(users.result)

  def insert(user: User): Future[Unit] = dbConfig.db.run(users += user).map {_ => ()}

  private class UserTable(tag: Tag) extends Table[User](tag, "user") {
    def id: Rep[Int] = column[Int]("id", O.PrimaryKey, O.AutoInc)
    def lastName: Rep[String] = column[String]("lastName")
    def firstName: Rep[String] = column[String]("firstName")
    def email: Rep[String] = column[String]("email")
    def password: Rep[String] = column[String]("password")
    def currency: Rep[String] = column[String]("currency")

    def * : ProvenShape[User] = (lastName, firstName, email, password, currency) <> (User.tupled, User.unapply)
    // make email unique for each user
    def emailIndex: Index = index("unique_email", email, unique = true)
  }
}
