import Dependencies._
val dotty = "0.23.0-RC1"
val scala213 = "2.13.1"
lazy val supportedScalaVersions = List(dotty, scala213)

inScope(Scope.GlobalScope)(
  List(
    organization := "us.oyanglul",
    licenses := Seq("Apache License 2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0")),
    homepage := Some(url("https://github.com/jcouyang/jujiu")),
    developers := List(
      Developer("jcouyang", "Jichao Ouyang", "oyanglulu@gmail.com", url("https://github.com/jcouyang"))
    ),
    scmInfo := Some(
      ScmInfo(
        url("https://github.com/jcouyang/jujiu"),
        "scm:git@github.com:jcouyang/jujiu.git"
      )
    ),
    pgpPublicRing := file(".") / ".gnupg" / "pubring.asc",
    pgpSecretRing := file(".") / ".gnupg" / "secring.asc",
    releaseEarlyWith := SonatypePublisher,
    scalaVersion := dotty
  )
)

lazy val deps = cats ++
        specs2 ++
        caffeine ++
        redis
lazy val root = (project in file("."))
  .settings(
    name := "Jujiu",
    scalacOptions ++= Seq("-Ykind-projector","-language:implicitConversions"),
    scalacOptions in Test -= "-Xfatal-warnings",
    crossScalaVersions := supportedScalaVersions,
    libraryDependencies ++= deps
  )

lazy val functionalTest = (project in file("."))
  .settings(
    name := "Jujiu",
    scalacOptions in Test -= "-Xfatal-warnings",
    scalaVersion := scala213,
    libraryDependencies ++= deps
  )
