package izumi.fundamentals.platform

import izumi.fundamentals.platform.language.IzScala
import izumi.fundamentals.platform.language.IzScala.ScalaRelease
import org.scalatest.wordspec.AnyWordSpec

class ScalaVersionTest extends AnyWordSpec {
  "ScalaVersion" should {
    "support comparison" in {
      import Ordering.Implicits._
      import IzScala.ScalaRelease._
      assert((IzScala.ScalaRelease.`2_12`(8): ScalaRelease) < (IzScala.ScalaRelease.`2_13`(0): ScalaRelease))
    }
  }
}
