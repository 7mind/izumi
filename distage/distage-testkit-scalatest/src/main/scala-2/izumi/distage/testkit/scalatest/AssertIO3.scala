package izumi.distage.testkit.scalatest

/** scalatest assertion macro for any [[izumi.functional.bio.IO3]] */
trait AssertIO3[F[-_, +_, +_]] extends AssertIO3Impl[F] {}

object AssertIO3 extends AssertIO3StaticImpl {}
