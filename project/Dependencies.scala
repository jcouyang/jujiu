import sbt._

object Dependencies {

  lazy val cats = Seq(
    "org.typelevel"   %% "cats-core"         % "1.6.0",
    "org.typelevel"   %% "cats-effect"       % "1.3.0"
  )

  lazy val caffeine = Seq(
    "org.scala-lang.modules" %% "scala-java8-compat" % "0.9.0",
    "com.github.ben-manes.caffeine" % "caffeine" % "2.7.0"
  )
  lazy val specs2 = Seq(
    "org.specs2"      %% "specs2-core"         % "4.3.4" % Test,
    "org.specs2"      %% "specs2-scalacheck"   % "4.3.4" % Test,
    "org.specs2"      %% "specs2-mock"   % "4.3.4" % Test
  )

  lazy val logs = Seq(
    "org.log4s"       %% "log4s"               % "1.8.0",
    "ch.qos.logback"  %  "logback-classic"     % "1.2.3"
  )
}
