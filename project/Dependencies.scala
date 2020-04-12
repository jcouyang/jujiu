import sbt._

object Dependencies {

  lazy val cats = Seq(
    "org.typelevel" %% "cats-core"   % "2.1.0",
    "org.typelevel" %% "cats-effect" % "2.1.2"
  )

  lazy val caffeine = Seq(
    "org.scala-lang.modules"        %% "scala-java8-compat" % "0.9.0",
    "com.github.ben-manes.caffeine" % "caffeine"            % "2.7.0"
  )
  lazy val specs2 = Seq(
    "org.specs2" %% "specs2-core"       % "4.8.3" % Test,
    "org.specs2" %% "specs2-scalacheck" % "4.8.3" % Test,
    "org.specs2" %% "specs2-mock"       % "4.8.3" % Test
  )

  lazy val redis = Seq(
    "redis.clients" % "jedis" % "3.0.1" % Test
  )

  lazy val logs = Seq(
    "org.log4s"      %% "log4s"          % "1.8.2",
    "ch.qos.logback" % "logback-classic" % "1.2.3"
  )
}
