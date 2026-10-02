package izumi.fundamentals.platform

import izumi.fundamentals.platform.language.ScalaRelease
import org.scalatest.wordspec.AnyWordSpec

class ScalaVersionTest extends AnyWordSpec {
  "ScalaVersion" should {
    "support comparison" in {
      import Ordering.Implicits._
      import ScalaRelease._
      assert((ScalaRelease.`2_12`(8): ScalaRelease) < (ScalaRelease.`2_13`(0): ScalaRelease))
    }
  }
}
