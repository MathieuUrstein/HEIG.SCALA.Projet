package bootstrap

import javax.inject.Inject

import play.api.db.slick.DatabaseConfigProvider
import play.db.NamedDatabase
import slick.backend.DatabaseConfig
import slick.driver.JdbcProfile

class InitialData @Inject()(@NamedDatabase("piggybank") dbConfigProvider: DatabaseConfigProvider) {
  val dbConfig: DatabaseConfig[JdbcProfile] = dbConfigProvider.get[JdbcProfile]

  /*dbConfig.db.run(DBIO.Seq(
    userDAO.users.
  ))*/
}
