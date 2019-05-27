import Dependencies._

ThisBuild / scalaVersion     := "2.12.8"
ThisBuild / version          := "0.1.0-SNAPSHOT"
ThisBuild / organization     := "us.oyanglul"
ThisBuild / organizationName := "Jichao Ouyang"

lazy val root = (project in file("."))
  .settings(
    name := "Jujiu",
    libraryDependencies ++=
      cats ++
      specs2 ++
      logs ++
      caffeine
  )
