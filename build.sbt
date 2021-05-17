import Dependencies._
val scala3 = "3.0.0"
val scala213 = "2.13.6"
lazy val supportedScalaVersions = List(scala3, scala213)

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
    scalaVersion := scala3
  )
)

val deps = cats ++ javaCompat ++ specs2
lazy val root = (project in file("."))
  .configs(IntegrationTest)
  .settings(
    Defaults.itSettings,
    name := "Jujiu",
    scalacOptions ++= Seq("-language:implicitConversions"),
    Test / scalacOptions -= "-Xfatal-warnings",
    crossScalaVersions := supportedScalaVersions,
    libraryDependencies ++= deps.map(_.cross(CrossVersion.for3Use2_13)) ++
      caffeine ++
      redis
  )
