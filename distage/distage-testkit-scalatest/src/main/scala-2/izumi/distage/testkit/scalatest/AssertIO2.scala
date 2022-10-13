package izumi.distage.testkit.scalatest

/** scalatest assertion macro for any [[izumi.functional.bio.IO2]] */
trait AssertIO2[F[+_, +_]] extends AssertIO2Impl[F] {}

object AssertIO2 extends AssertIO2StaticImpl {}
