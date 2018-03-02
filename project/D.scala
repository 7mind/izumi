import sbt._

object D {

  object R {
    val scala212 = "2.12.4"

    val scala_reflect = "org.scala-lang" % "scala-reflect" % scala212
    //val scala_asm = "org.scala-lang.modules" % "scala-asm" % "6.0.0-scala-1"
    //val scala_arm = "com.jsuereth" %% "scala-arm" % "2.0"
    val scalameta = "org.scalameta" %% "scalameta" % "3.3.1"

    val scala_compiler =  "org.scala-lang" % "scala-compiler" % "2.12.4"
    val scala_library =  "org.scala-lang" % "scala-library" % "2.12.4"

    // TODO: temporary (I hope) solution, we have to ship our own cglib depending on scala-asm
    val cglib_nodep = "cglib" % "cglib-nodep" % "3.2.6"

    val slf4j_api = "org.slf4j" % "slf4j-api" % "1.7.25"
    val slf4j_simple = "org.slf4j" % "slf4j-simple" % "1.7.25"
    val json4s_native = "org.json4s" %% "json4s-native" % "3.5.3"

    //val parboiled = "org.parboiled" %% "parboiled" % "2.1.4"
    val fastparse = "com.lihaoyi" %% "fastparse" % "1.0.0"
    private val scala_java8_compat = "org.scala-lang.modules" %% "scala-java8-compat" % "0.8.0"

    val cats_version = "1.0.1"
    val cats_core = "org.typelevel" %% "cats-core" % cats_version

    val circe_version = "0.9.1"
    val circe_all: Seq[ModuleID] = Seq(
      "io.circe" %% "circe-core"
      , "io.circe" %% "circe-generic"
      , "io.circe" %% "circe-generic-extras"
      , "io.circe" %% "circe-parser"
    ).map(_ % circe_version)

    val http4s_version = "0.18.0"
    val http4s_all: Seq[ModuleID] = Seq(
      "org.http4s" %% "http4s-dsl"
      , "org.http4s" %% "http4s-circe"
      , "org.http4s" %% "http4s-blaze-server"
    ).map(_ % http4s_version)

    val essentials = Seq(scala_java8_compat)
  }

  object T {
    private val scalatest = "org.scalatest" %% "scalatest" % "3.0.4" % "test"
    val scala_compiler = R.scala_compiler % "test"
    val scala_library = R.scala_library % "test"
    val slf4j_simple = R.slf4j_simple % "test"
    val essentials = Seq(scalatest)
  }

}
