import sbtcrossproject.CrossPlugin.autoImport.{crossProject, CrossType}

ThisBuild / scalaVersion := sys.props("izumi.fixture.scala-version")
ThisBuild / organization := "izumi.local.fixtures"
ThisBuild / version := "0.0.0"
ThisBuild / publish / skip := true

lazy val consumer = crossProject(JVMPlatform, JSPlatform, NativePlatform).crossType(CrossType.Pure).in(file("consumer"))
  .settings(
    libraryDependencies += "io.7mind.izumi" %% "distage-test-runner" % sys.props("izumi.fixture.version"),
    scalacOptions ++= {
      if (scalaVersion.value.startsWith("3.")) Seq("-release:17", "-Ybackend-parallelism", "1") else Seq("-release:17", "-Xsource:3")
    },
  )
  .jvmSettings(
    Compile / unmanagedSourceDirectories += baseDirectory.value.getParentFile / "src/main/scala-jvm-native",
  )
  .nativeSettings(
    Compile / unmanagedSourceDirectories += baseDirectory.value.getParentFile / "src/main/scala-jvm-native",
  )
  .jsSettings(scalaJSUseMainModuleInitializer := true)

lazy val consumerJVM = consumer.jvm
lazy val consumerJS = consumer.js
lazy val consumerNative = consumer.native
