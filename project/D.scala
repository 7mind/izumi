import sbt._

object D {

  object R {
    val scala212 = "2.12.5"
    val scala_compiler =  "org.scala-lang" % "scala-compiler" % scala212
    val scala_library =  "org.scala-lang" % "scala-library" % scala212
    val scala_reflect = "org.scala-lang" % "scala-reflect" % scala212

    //val scala_asm = "org.scala-lang.modules" % "scala-asm" % "6.0.0-scala-1"
    //val scala_arm = "com.jsuereth" %% "scala-arm" % "2.0"

    private val scala_java8_compat = "org.scala-lang.modules" %% "scala-java8-compat" % "0.8.0"
    val essentials = Seq(scala_java8_compat)

    val cats_version = "1.1.0"
    val cats_core = "org.typelevel" %% "cats-core" % cats_version

    val scalameta = "org.scalameta" %% "scalameta" % "3.3.1" // TODO: can't shade scalameta https://github.com/coursier/coursier/issues/801
    val cglib_nodep = "cglib" % "cglib-nodep" % "3.2.6" // TODO: It would be good to completely get rid of cglib and build our own proxy generator on top of scala-asm
    val fastparse = "com.lihaoyi" %% "fastparse" % "1.0.0" % "shaded"
    val json4s_native = "org.json4s" %% "json4s-native" % "3.5.3"

    val circeVersion = "0.9.1"

    val circe: Seq[ModuleID] = Seq(
      "io.circe" %% "circe-core"
      , "io.circe" %% "circe-generic"
      , "io.circe" %% "circe-generic-extras"
      , "io.circe" %% "circe-parser"
      , "io.circe" %% "circe-java8"
    ).map(_ % circeVersion)


    val slf4j_api = "org.slf4j" % "slf4j-api" % "1.7.25"
    val slf4j_simple = "org.slf4j" % "slf4j-simple" % "1.7.25"
  }

  object T {
    private val scalatest = "org.scalatest" %% "scalatest" % "3.0.4" % "test"
    val scala_compiler = R.scala_compiler % "test"
    val scala_library = R.scala_library % "test"
    val slf4j_simple = R.slf4j_simple % "test"
    val essentials = Seq(scalatest)
  }

}
