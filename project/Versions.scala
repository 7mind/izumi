object V {
  // foundation
  val collection_compat = "2.1.3"

  val kind_projector = "0.11.0"
  val scalatest = "3.1.0"

  val boopickle = "1.3.1"

  val shapeless = "2.3.3"

  val cats = "2.0.0"
  val cats_effect = "2.0.0"
  val zio = "1.0.0-RC17"
  val zio_interop_cats = "2.0.0.0-RC10"

  val circe = "0.12.3"
  val circe_generic_extras = "0.12.2"
  val circe_derivation = "0.12.0-M7"
  val circe_config = "0.7.0"
  val jawn = "0.14.3"

  val http4s = "0.21.0-M6"

  val scalameta = "4.3.0"
  val fastparse = "2.2.3"

  val scala_xml = "1.2.0"

  // java-only dependencies below
  // java, we need it bcs http4s ws client isn't ready yet
  val asynchttpclient = "2.10.4"

  val classgraph = "4.8.60"
  val slf4j = "1.7.30"
  val typesafe_config = "1.4.0"

  // good to drop - java
  val cglib_nodep = "3.3.0"
  val scala_java_time = "2.0.0-RC3"
  val docker_java = "3.2.0-rc2"
}
