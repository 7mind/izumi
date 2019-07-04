package com.github.pshirshov.izumi.sbt.deps

import sbt._

object IzumiDeps {

  object V {
    // foundation
    val scala_212 = "2.12.8"
    val scala_213 = "2.13.0"

    val collection_compat = "2.1.1"

    val kind_projector = "0.10.3"
    val scalatest = "3.0.8"

    val shapeless = "2.3.3"

    val cats = "2.0.0-M4"
    val zio = "1.0.0-RC8-12"

    val circe = "0.12.0-M4"
    val jawn = "0.14.2"

    val http4s = "0.21.0-M1"

    val scalameta = "4.2.0"
    val fastparse = "2.1.3"

    val scala_xml = "1.2.0"

    // java-only dependencies below
    // java, we need it bcs http4s ws client isn't ready yet
    val asynchttpclient = "2.10.1"

    val classgraph = "4.8.43"
    val slf4j = "1.7.26"
    val typesafe_config = "1.3.4"

    // good to drop - java
    val cglib_nodep = "3.2.12"

  }

  object R {
    val scala_compiler = "org.scala-lang" % "scala-compiler"
    val scala_library = "org.scala-lang" % "scala-library"
    val scala_reflect = "org.scala-lang" % "scala-reflect"
    val scala_xml = "org.scala-lang.modules" %% "scala-xml" % V.scala_xml

    //val scalacheck = "org.scalacheck" %% "scalacheck" % V.scalacheck

    val collection_compat = "org.scala-lang.modules" %% "scala-collection-compat" % V.collection_compat

    val zio_core: ModuleID = "dev.zio" %% "zio" % V.zio
    val zio_interop: ModuleID = "dev.zio" %% "zio-interop-cats" % V.zio

    val essentials: Seq[ModuleID] = Seq(collection_compat)

    val kind_projector = "org.typelevel" % "kind-projector" % V.kind_projector cross CrossVersion.binary

    val fast_classpath_scanner = "io.github.classgraph" % "classgraph" % V.classgraph

    val typesafe_config = "com.typesafe" % "config" % V.typesafe_config

    val cats_core = "org.typelevel" %% "cats-core" % V.cats
    val cats_effect = "org.typelevel" %% "cats-effect" % V.cats
    val cats_all: Seq[ModuleID] = Seq(
      cats_core
      , cats_effect
    )

    // TODO: can't shade scalameta https://github.com/coursier/coursier/issues/801
    val scalameta = "org.scalameta" %% "scalameta" % V.scalameta

    // TODO: It would be good to completely get rid of cglib and build our own proxy generator on top of scala-asm
    val cglib_nodep = "cglib" % "cglib-nodep" % V.cglib_nodep
    val fastparse = "com.lihaoyi" %% "fastparse" % V.fastparse

    val shapeless = "com.chuusai" %% "shapeless" % V.shapeless

    val circe: Seq[ModuleID] = Seq(
      "io.circe" %% "circe-core",
      "io.circe" %% "circe-generic",
      "io.circe" %% "circe-generic-extras",
      "io.circe" %% "circe-parser",
      "io.circe" %% "circe-literal",
      "io.circe" %% "circe-derivation",
    ).map(_ % V.circe)

    val http4s_client: Seq[ModuleID] = Seq(
      "org.http4s" %% "http4s-blaze-client"
    ).map(_ % V.http4s)

    val http4s_server: Seq[ModuleID] = Seq(
      "org.http4s" %% "http4s-dsl"
      , "org.http4s" %% "http4s-circe"
      , "org.http4s" %% "http4s-blaze-server"
    ).map(_ % V.http4s)

    val http4s_all: Seq[ModuleID] = http4s_server ++ http4s_client

    val asynchttpclient = "org.asynchttpclient" % "async-http-client" % V.asynchttpclient

    val slf4j_api = "org.slf4j" % "slf4j-api" % V.slf4j
    val slf4j_simple = "org.slf4j" % "slf4j-simple" % V.slf4j

    val scalatest = "org.scalatest" %% "scalatest" % V.scalatest
  }

  object C {
    val jawn = "org.typelevel" %% "jawn-parser" % V.jawn % Compile
  }

  object T {
    val scalatest = R.scalatest % Test
    //val scalacheck = R.scalacheck % Test
    val slf4j_simple = R.slf4j_simple % Test

    val essentials: Seq[ModuleID] = Seq(
      scalatest,
      //scalacheck,
    )

    val circe: Seq[ModuleID] = R.circe.map(_ % Test)
    val cats_all: Seq[ModuleID] = R.cats_all.map(_ % Test)
    val zio_core = R.zio_core % Test
  }

}

object IzumiDepsPlugin extends AutoPlugin {
  override def trigger = allRequirements

  object autoImport {
    val IzumiRootDeps: IzumiDeps.type = com.github.pshirshov.izumi.sbt.deps.IzumiDeps
  }

}
