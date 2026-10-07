ThisBuild / scalaVersion := sys.props("izumi.fixture.scala-version")
ThisBuild / publish / skip := true
ThisBuild / scalacOptions ++= {
  if (scalaVersion.value.startsWith("3.")) Seq("-release:17", "-Ybackend-parallelism", "1")
  else Seq("-release:17", "-Xsource:3")
}

lazy val application = project.settings(
  libraryDependencies += "io.7mind.izumi" %% "distage-framework" % sys.props("izumi.fixture.framework-version")
)

lazy val checks = project.dependsOn(application).settings(
  libraryDependencies += "io.7mind.izumi" %% "distage-testkit-runner" % sys.props("izumi.fixture.version")
)

lazy val root = project.in(file(".")).aggregate(application, checks)
