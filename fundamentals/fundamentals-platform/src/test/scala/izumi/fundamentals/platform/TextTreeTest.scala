package izumi.fundamentals.platform

import izumi.fundamentals.platform.TextTreeTest.*
import izumi.fundamentals.platform.strings.TextTree
import izumi.fundamentals.platform.strings.TextTree.*
import org.scalatest.wordspec.AnyWordSpec

import scala.language.implicitConversions

class TextTreeTest extends AnyWordSpec {
  "TxtTree" should {
    "properly handle interpolations" in {
      assert(
        q"test1 ${TestVal("1")} test2 ${TestVal("2")} test3".dump == "test1 TestVal(1) test2 TestVal(2) test3"
      )

      assert(
        q"${TestVal("1")} test2 ${TestVal("2")}".dump == "TestVal(1) test2 TestVal(2)"
      )

      assert(q"${TestVal("1")}".dump == "TestVal(1)")

      assert((q"test": TextTree[Nothing]).dump == "test")
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
        t3.map(v => TestVal2(v.value)).dump == "t1: TestVal2(1), t2: test, t3: TestVal2(3)"
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

    "support newlines" in {
      val t = q"test\ntest ${1}"
      assert(t.mapRender(v => v.toString) == "test\ntest 1")
    }

    "support joins" in {
      val t1: TextTree[Int] = Seq(q"a", q"b").join(":")
      assert(t1.mapRender(_.toString) == "a:b")

      val t2: TextTree[Int] = Seq(q"a", q"b").join("{\n", ":", "\n}")
      assert(t2.mapRender(_.toString) == "{\n  a:b\n}")

      val t3: TextTree[Int] = Seq(q"a", q"b").join("{\n", ":", "\n}", None)
      assert(t3.mapRender(_.toString) == "{\na:b\n}")
    }

    "support upcasts" in {
      val v = Sub1()

      val t1 = q"$v".as[Wrap]
      assert(t1.dump.nonEmpty)

//      val t2: TextTree[Wrap] = q"$v"
//      assert(t2.dump.nonEmpty)

      val vv1 = v: InterpolationArg[Wrap]
      assert(vv1.asNode.dump.nonEmpty)

      def accept(t: TextTree[Wrap]) = assert(t.dump.nonEmpty)

      val t3: TextTree[Wrap] = q"$v ${WSub(v)}"
      assert(t3.dump.nonEmpty)
      accept(t3)

      val t4 = q"$v ${WSub(v)}"
      assert(t4.dump.nonEmpty)
      accept(t4)
    }
  }
}

object TextTreeTest {
  case class TestVal(value: String)
  case class TestVal2(value: String)

  sealed trait Sub
  case class Sub1() extends Sub

  sealed trait Wrap
  case class WSub(sub: Sub) extends Wrap

  object Wrap {
    implicit def upcast_sub(sub: Sub): Wrap = WSub(sub)
    implicit def xxx(x: InterpolationArg[Sub]): InterpolationArg[Wrap] = {
      val _ = x
      ???
    }

//    implicit def arg_from_sub(sub: Sub)(implicit conv: Sub => Wrap): InterpolationArg[Wrap] = new InterpolationArg[Wrap] {
//      override def asNode: TextTree[Wrap] = ValueNode[Wrap](sub)
//    }

  }

}
