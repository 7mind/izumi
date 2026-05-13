package izumi.functional.bio

import org.scalatest.wordspec.AnyWordSpec

final class SubmergedTypedErrorTest extends AnyWordSpec {

  private trait FA[A]
  private trait FB[A]

  "SubmergedTypedError" should {

    "extract payload when TagK matches (same-F round-trip)" in {
      val e = SubmergedTypedError[FA](42)
      assert(SubmergedTypedError.unapply[FA](e) == Some(42))
    }

    "return None when TagK differs (different-F extraction)" in {
      val e = SubmergedTypedError[FA]("hello")
      assert(SubmergedTypedError.unapply[FB](e) == None)
    }

    "be idempotent when wrapping same-F SubmergedTypedError (no double wrapping)" in {
      val inner = SubmergedTypedError[FA](42)
      val outer = SubmergedTypedError[FA](inner)
      assert(outer eq inner)
    }

    "wrap a different-F SubmergedTypedError in a new outer (different-F nesting)" in {
      val existingFB = SubmergedTypedError[FB](42)
      val wrappedFA  = SubmergedTypedError[FA](existingFB)
      assert(wrappedFA ne existingFB)
      assert(SubmergedTypedError.unapply[FA](wrappedFA) == Some(existingFB))
      assert(SubmergedTypedError.unapply[FB](wrappedFA) == None)
    }

    "accept non-Throwable payloads (String, Int, case class)" in {
      val s = SubmergedTypedError[FA]("hello")
      assert(s.payload == "hello")
      assert(s.getCause == null)

      val i = SubmergedTypedError[FA](42)
      assert(i.payload == 42)
      assert(i.getCause == null)

      case class MyError(msg: String)
      val ce = SubmergedTypedError[FA](MyError("oops"))
      assert(ce.payload == MyError("oops"))
      assert(ce.getCause == null)
    }

    "chain the cause when payload is a Throwable" in {
      val cause = new RuntimeException("boom")
      val e = SubmergedTypedError[FA](cause)
      assert(e.getCause eq cause)
    }

    "produce an empty stack trace (writableStackTrace = false)" in {
      val e = SubmergedTypedError[FA](99)
      assert(e.getStackTrace.length == 0)
    }

    "include payload class name in getMessage" in {
      val e = SubmergedTypedError[FA](42)
      assert(e.getMessage.contains("java.lang.Integer"))
    }

  }

}
