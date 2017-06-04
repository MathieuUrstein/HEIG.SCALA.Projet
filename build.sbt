name := "PiggyBank"

version := "1.0"

lazy val `piggybank` = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.11.7"

libraryDependencies += "com.typesafe.play" %% "play-slick" % "2.0.0"

libraryDependencies ++= Seq(
  cache,
  ws,
  specs2 % Test,
  "com.typesafe.play" %% "play-slick" % "2.0.0",
  "com.typesafe.play" %% "play-slick-evolutions" % "2.0.0",
  "org.xerial" % "sqlite-jdbc" % "latest.release",
  // to hash passwords
  "org.mindrot" % "jbcrypt" % "latest.release",
  // to use directly JWT based authentication
  "com.pauldijou" %% "jwt-play" % "latest.release"
)

unmanagedResourceDirectories in Test <+= baseDirectory(_ / "target/web/public/test")

resolvers += "scalaz-bintray" at "https://dl.bintray.com/scalaz/releases"
