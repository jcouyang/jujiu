import sbt._

object Dependencies {

  lazy val cats = Seq(
    "org.typelevel" %% "cats-core"   % "2.1.0",
    "org.typelevel" %% "cats-effect" % "3.3.1"
  )

  lazy val javaCompat = Seq(
    "org.scala-lang.modules" %% "scala-java8-compat" % "1.0.2"
  )
  lazy val caffeine = Seq(
    "com.github.ben-manes.caffeine" % "caffeine" % "2.9.3"
  )
  lazy val specs2 = Seq(
    "org.specs2"     %% "specs2-core" % "4.13.1" % "it,test",
    "org.scalacheck" %% "scalacheck"  % "1.15.4" % "it,test",
    "org.specs2"     %% "specs2-mock" % "4.13.1" % "it,test"
  )

  lazy val redis = Seq(
    "redis.clients" % "jedis" % "4.0.0" % Test
  )

  lazy val logs = Seq(
    "org.log4s" %% "log4s" % "1.8.2"
  )
}
