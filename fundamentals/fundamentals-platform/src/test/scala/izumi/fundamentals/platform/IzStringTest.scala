package izumi.fundamentals.platform

import izumi.fundamentals.platform.strings.IzString._
import org.scalatest.wordspec.AnyWordSpec

class IzStringTest extends AnyWordSpec {

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
      assert("x".leftEllipsed(5, "...") == "x")
      assert("xxxxxx".leftEllipsed(5, "...") == "...xx")
      assert("xx".leftEllipsed(1, "...") == "x")
    }

    "support ellipsed rightpad" in {
      assert("x".rightEllipsed(5, "...") == "x")
      assert("xxxxxx".rightEllipsed(5, "...") == "xx...")
      assert("xx".rightEllipsed(1, "...") == "x")
    }

    "support minimization" in {
      assert("x".minimize(0) == "x")
      assert("x.y.z".minimize(0) == "x.y.z")
      assert("x..z".minimize(0) == "x.z")
      assert("com.github.izumi.Class".minimize(0) == "c.g.i.C")

      assert("x".minimize(1) == "x")
      assert("x.y.z".minimize(1) == "x.y.z")
      assert("x..z".minimize(1) == "x.z")
      assert("com.github.izumi.Class".minimize(1) == "c.g.i.Class")
      assert("com.github.izumi.Class".minimize(2) == "c.g.izumi.Class")
    }

    "support splitFirst" in {
      assert("=1=2".splitFirst('=') == "" -> "1=2")
      assert("1=".splitFirst('=') == "1" -> "")
      assert("key=value=xxx".splitFirst('=') == "key" -> "value=xxx")
    }

    "support splitLast" in {
      assert("=1=2".splitLast('=') == "=1" -> "2")
      assert("1=".splitLast('=') == "1" -> "")
      assert("key=value=xxx".splitLast('=') == "key=value" -> "xxx")
    }

  }

}
