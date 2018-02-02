
package com.github.pshirshov.izumi.fundamentals.platform

import com.github.pshirshov.izumi.fundamentals.platform.strings.IzString._
import org.scalatest.WordSpec

class IzStringTest extends WordSpec {

  "Extended string" should {
    "support boolean parsing" in {
      assert("false".asBoolean().contains(false))
      assert("true".asBoolean().contains(true))
      
      assert("true".asBoolean(true))
      assert("x".asBoolean(true))
      assert(!"x".asBoolean(false))
      assert(!"false".asBoolean(false))

      assert(null.asInstanceOf[String].asBoolean().isEmpty)
      assert(null.asInstanceOf[String].asBoolean(true))
      assert(!null.asInstanceOf[String].asBoolean(false))
    }

    "support ellipsed leftpad" in {
      assert("x".ellipsedLeftPad(5) == "    x")
      assert("xxxxxx".ellipsedLeftPad(5) == "...xx")
      assert("xx".ellipsedLeftPad(1) == "x")
    }
  }

  
}