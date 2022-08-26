package izumi.distage.testkit.scalatest

import izumi.functional.bio.IO3
import org.scalactic.{Prettifier, source}
import org.scalatest.Assertion

/** scalatest assertion macro for any [[izumi.functional.bio.IO3]] */
trait AssertIO3[F[-_, +_, +_]] extends AssertIO3Impl[F] {}

object AssertIO3 extends AssertIO3StaticImpl {}
