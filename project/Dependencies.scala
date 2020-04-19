import sbt._

object Dependencies {

  lazy val cats = Seq(
    "org.typelevel" % "cats-core_2.13"   % "2.1.0",
    "org.typelevel" % "cats-effect_2.13" % "2.1.2"
  )

  lazy val caffeine = Seq(
    "org.scala-lang.modules"        % "scala-java8-compat_2.13" % "0.9.1",
    "com.github.ben-manes.caffeine" % "caffeine"            % "2.7.0"
  )
  lazy val specs2 = Seq(
    "org.specs2" % "specs2-core_2.13"       % "4.8.3" % "it,test",
    "org.scalacheck" % "scalacheck_2.13" % "1.14.3" % "it,test",
    "org.specs2" % "specs2-mock_2.13"       % "4.8.3" % "it,test"
  )

  lazy val redis = Seq(
    "redis.clients" % "jedis" % "3.0.1" % Test
  )

  lazy val logs = Seq(
    "org.log4s"      % "log4s_2.13"          % "1.8.2",
    "ch.qos.logback" % "logback-classic" % "1.2.3"
  )
}
