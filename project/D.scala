import sbt._

object D {

  object R {
    val scala212 = "2.12.6"
    val scala_compiler = "org.scala-lang" % "scala-compiler" % scala212
    val scala_library = "org.scala-lang" % "scala-library" % scala212
    val scala_reflect = "org.scala-lang" % "scala-reflect" % scala212

    //val scala_asm = "org.scala-lang.modules" % "scala-asm" % "6.0.0-scala-1"
    //val scala_arm = "com.jsuereth" %% "scala-arm" % "2.0"

    private val scala_java8_compat = "org.scala-lang.modules" %% "scala-java8-compat" % "0.9.0"
    val essentials = Seq(scala_java8_compat)

    val kind_projector = "org.spire-math" % "kind-projector" % "0.9.6" cross CrossVersion.binary

    val better_files = "com.github.pathikrit" %% "better-files" % "3.4.0"

    val fast_classpath_scanner = "io.github.lukehutch" % "fast-classpath-scanner" % "2.21"

    val typesafe_config = "com.typesafe" % "config" % "1.3.3"

    val kamon = Seq(
      "io.kamon" %% "kamon-core" % "1.0.0"
      , "io.kamon" %% "kamon-jmx" % "0.6.7"
    )

    private val cats_version = "1.1.0"
    private val cats_effect_version = "0.10.1"
    val cats_kernel = "org.typelevel" %% "cats-kernel" % cats_version
    val cats_all: Seq[ModuleID] = Seq(
      "org.typelevel" %% "cats-core"
    ).map(_ % cats_version) ++ Seq(
      "org.typelevel" %% "cats-effect"
    ).map(_ % cats_effect_version)


    val scalameta = "org.scalameta" %% "scalameta" % "3.7.4" // TODO: can't shade scalameta https://github.com/coursier/coursier/issues/801
    val cglib_nodep = "cglib" % "cglib-nodep" % "3.2.6" // TODO: It would be good to completely get rid of cglib and build our own proxy generator on top of scala-asm
    val fastparse = "com.lihaoyi" %% "fastparse" % "1.0.0" % "shaded"
    val json4s_native = "org.json4s" %% "json4s-native" % "3.5.3"

    val shapeless = "com.chuusai" %% "shapeless" % "2.3.3"
    private val circeVersion = "0.9.3"
    private val circeDerivationVersion = "0.9.0-M3"

    val circe: Seq[ModuleID] = Seq(
      "io.circe" %% "circe-core"
      , "io.circe" %% "circe-generic"
      , "io.circe" %% "circe-generic-extras"
      , "io.circe" %% "circe-parser"
      , "io.circe" %% "circe-java8"
    ).map(_ % circeVersion) ++ Seq(
      "io.circe" %% "circe-derivation" % circeDerivationVersion)

    val http4s_version = "0.18.11"
    val http4s_client: Seq[ModuleID] = Seq(
      "org.http4s" %% "http4s-blaze-client"
    ).map(_ % http4s_version)
    val http4s_server: Seq[ModuleID] = Seq(
      "org.http4s" %% "http4s-dsl"
      , "org.http4s" %% "http4s-circe"
      , "org.http4s" %% "http4s-blaze-server"
    ).map(_ % http4s_version)

    val http4s_all: Seq[ModuleID] = http4s_server ++ http4s_client

    val slf4j_api = "org.slf4j" % "slf4j-api" % "1.7.25"
    val slf4j_simple = "org.slf4j" % "slf4j-simple" % "1.7.25"

    val scallop = "org.rogach" %% "scallop" % "3.1.2"
  }

  object T {
    private val scalatest = "org.scalatest" %% "scalatest" % "3.0.5" % "test"
    val scala_compiler = R.scala_compiler % "test"
    //val scala_library = R.scala_library % "test"
    val slf4j_simple = R.slf4j_simple % "test"
    val essentials = Seq(scalatest)

    val circe: Seq[ModuleID] = R.circe.map(_ % "test")
    val cats_all: Seq[ModuleID] = R.cats_all.map(_ % "test")
  }

}
