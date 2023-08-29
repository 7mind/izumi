package izumi.fundamentals.platform.strings

import izumi.fundamentals.platform.strings.TextTree.*
import izumi.fundamentals.platform.strings.TxtTreeTest.{TestVal, TestVal2}
import org.scalatest.wordspec.AnyWordSpec

class TxtTreeTest extends AnyWordSpec {
  "TxtTree" should {
    "properly handle interpolations" in {
      assert(
        q"test1 ${TestVal("1")} test2 ${TestVal("2")} test3".dump == "test1 TestVal(1) test2 TestVal(2) test3"
      )

      assert(
        q"${TestVal("1")} test2 ${TestVal("2")}".dump == "TestVal(1) test2 TestVal(2)"
      )

      assert(q"${TestVal("1")}".dump == "TestVal(1)")

      assert((q"test": Node[Nothing]).dump == "test")
      assert(q"test".dump == "test")
      assert(q"".dump == "")
    }

    "handle tree nesting" in {
      val t1 = q"${TestVal("1")}"
      val t2 = q"test"

      val t3 =
        q"t1: $t1, t2: $t2, t3: ${TestVal("3")}"

      assert(t3.dump == "t1: TestVal(1), t2: test, t3: TestVal(3)")
      assert(t3.dump == t3.flatten.dump)
      assert(
        t3.map(v => TestVal2(v.value))
          .dump == "t1: TestVal2(1), t2: test, t3: TestVal2(3)"
      )
    }

    "handle margin removal" in {
      val t1 = q"${TestVal("1")}"
      val t2 = q"test"

      val t3 =
        q""" t1: $t1,
           | t2: $t2,
           | t3: ${TestVal("3")}""".stripMargin

      assert(
        t3.dump ==
          """ t1: TestVal(1),
          | t2: test,
          | t3: TestVal(3)""".stripMargin
      )
    }
  }
}

object TxtTreeTest {
  case class TestVal(value: String)
  case class TestVal2(value: String)
}
