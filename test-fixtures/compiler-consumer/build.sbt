ThisBuild / scalaVersion := sys.props("izumi.fixture.scala-version")
ThisBuild / scalacOptions ++= Seq("-release:17", "-Xkind-projector:underscores", "-Ybackend-parallelism", "1")
ThisBuild / publish / skip := true

lazy val application = project
  .settings(
    libraryDependencies += "io.7mind.izumi" %% "distage-framework" % sys.props("izumi.fixture.version")
  )

lazy val checks = project
  .dependsOn(application)

lazy val root = project.in(file("."))
  .aggregate(application, checks)
