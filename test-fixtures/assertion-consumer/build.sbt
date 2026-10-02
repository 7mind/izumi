import sbtcrossproject.CrossPlugin.autoImport.{crossProject, CrossType}

ThisBuild / scalaVersion := sys.props("izumi.fixture.scala-version")
ThisBuild / organization := "izumi.local.fixtures"
ThisBuild / version := "0.0.0"
ThisBuild / publish / skip := true

lazy val consumer = crossProject(JVMPlatform, JSPlatform, NativePlatform).crossType(CrossType.Pure).in(file("consumer"))
  .settings(
    libraryDependencies += "io.7mind.izumi" %% "fundamentals-assertions" % sys.props("izumi.fixture.version"),
    libraryDependencies += "io.7mind.izumi" %% "fundamentals-assertions-cats" % sys.props("izumi.fixture.version"),
    scalacOptions ++= {
      if (scalaVersion.value.startsWith("3.")) Seq("-release:17", "-Ybackend-parallelism", "1") else Seq("-release:17")
    },
  )
  .jsSettings(scalaJSUseMainModuleInitializer := true)

lazy val consumerJVM = consumer.jvm
lazy val consumerJS = consumer.js
lazy val consumerNative = consumer.native

lazy val bioConsumer = crossProject(JVMPlatform, JSPlatform).crossType(CrossType.Pure).in(file("bio-consumer"))
  .settings(
    libraryDependencies ++= Seq(
      "io.7mind.izumi" %% "fundamentals-assertions-bio" % sys.props("izumi.fixture.version"),
      "dev.zio" %% "zio" % "2.1.24",
      "dev.zio" %% "izumi-reflect" % "3.0.8",
    ),
    scalacOptions ++= Seq("-release:17", "-Ybackend-parallelism", "1"),
  )
  .jsSettings(
    scalaJSUseMainModuleInitializer := true,
    libraryDependencies += "io.github.cquiroz" %% "scala-java-time" % "2.6.0",
  )

lazy val bioConsumerJVM = bioConsumer.jvm
lazy val bioConsumerJS = bioConsumer.js
