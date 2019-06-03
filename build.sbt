import Dependencies._

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
    pgpPublicRing := file("/home/circleci/repo/.gnupg/pubring.asc"),
    pgpSecretRing := file("/home/circleci/repo/.gnupg/secring.asc"),
    releaseEarlyWith := SonatypePublisher,
    scalaVersion := "2.12.8"
  )
)

lazy val root = (project in file("."))
  .settings(
    name := "Jujiu",
    libraryDependencies ++=
      cats ++
        specs2 ++
        logs ++
        caffeine ++
        redis
  )
