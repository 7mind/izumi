package com.github.pshirshov.izumi.fundamentals.platform

import com.github.pshirshov.izumi.fundamentals.platform.language.IzScala
import com.github.pshirshov.izumi.fundamentals.platform.language.IzScala.ScalaRelease
import org.scalatest.WordSpec

class ScalaVersionTest extends WordSpec {
  "ScalaVersion" should {
    "support comparison" in {
      import Ordering.Implicits._
      import IzScala.ScalaRelease._
      assert((IzScala.ScalaRelease.`2_12`(8): ScalaRelease) < (IzScala.ScalaRelease.`2_13`(0): ScalaRelease))
    }
  }
}
