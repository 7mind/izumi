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
      def accept(t: TextTree[Wrap]) = assert(t.dump.nonEmpty)

      val v = Sub1()

      val t1 = q"$v".as[Wrap]
      accept(t1)

      val t3 = q"${v: Wrap}"
      accept(t3)

      {
        import Wrap.conversion.*

        val t2: TextTree[Wrap] = q"$v"
        accept(t2)

        val vv1 = v: InterpolationArg[Wrap]
        assert(vv1.asNode.dump.nonEmpty)

        val t4: TextTree[Wrap] = q"$v ${WSub(v)}"
        accept(t4)

        val t5 = q"$v ${WSub(v)}"
        accept(t5)
      }
    }

    "support emptiness check" in {
      val q1 = q""
      val q2 = q"$q1"
      val q3 = q"x$q1"

      assert(q1.isEmpty)
      assert(q2.isEmpty)
      assert(q3.nonEmpty)
    }

    "support qv (verbatim) interpolator" in {
      // In q interpolator, \n is processed as newline
      val q1 = q"test\nline"
      assert(q1.render == "test\nline")

      // In qv interpolator, \n is kept as literal backslash-n
      val qv1 = qv"test\nline"
      assert(qv1.render == "test\\nline")

      // Test with interpolations
      val qv2 = qv"test ${TestVal("1")} value\n"
      assert(qv2.dump == "test TestVal(1) value\\n")

      // Test escapes are NOT processed in verbatim
      val qv3 = qv"tab\there"
      assert(qv3.render == "tab\\there")

      // Compare with regular q
      val q2 = q"tab\there"
      assert(q2.render == "tab\there")
    }

    "support verbatim nodes with stripMargin" in {
      val qv1 = qv""" line1\n
                    | line2\n
                    | line3""".stripMargin
      assert(qv1.render == " line1\\n\n line2\\n\n line3")
    }

    "support shift operation" in {
      val t1 = q"line1\nline2"
      val shifted = t1.shift(2)
      assert(shifted.render == "  line1\n  line2")

      val t2 = q"line1\nline2\nline3"
      val shifted2 = t2.shift(4)
      assert(shifted2.render == "    line1\n    line2\n    line3")
    }

    "support trim operation" in {
      val t1 = q"  test  "
      val trimmed = t1.trim
      assert(trimmed.render == "test")

      val t2 = q"\n  value\n  "
      val trimmed2 = t2.trim
      assert(trimmed2.render == "value")
    }

    "support nested shift operations" in {
      val t1 = q"test"
      val nested = t1.shift(2).shift(2)
      assert(nested.render == "    test")
    }

    "support nested trim operations" in {
      val t1 = q"  test  "
      val nested = t1.trim.trim
      assert(nested.render == "test")
    }

    "support foreach operation" in {
      var collected = Seq.empty[String]
      val t = q"a ${TestVal("1")} b ${TestVal("2")} c"
      t.foreach(v => collected = collected :+ v.value)
      assert(collected == Seq("1", "2"))

      // Empty tree
      var count = 0
      q"no values here".foreach((_: Any) => count += 1)
      assert(count == 0)
    }

    "support values extraction" in {
      val t = q"a ${TestVal("1")} b ${TestVal("2")} c"
      val vals = t.values
      assert(vals == Seq(TestVal("1"), TestVal("2")))

      // Empty
      assert(q"text only".values.isEmpty)

      // With nested trees
      val t2 = q"start ${q"nested ${TestVal("n1")}"} end ${TestVal("e1")}"
      assert(t2.values == Seq(TestVal("n1"), TestVal("e1")))
    }

    "support last character detection" in {
      assert(q"test".last.contains('t'))
      // In Scala string literals, \n is already processed by the compiler
      // So the string contains an actual newline character
      val withNewline = q"test${"\n"}"
      assert(withNewline.last.contains('\n'))
      assert(q"".last.isEmpty)
      assert(q"${TestVal("x")}".last.isEmpty)

      val t1 = q"prefix ${TestVal("val")} suffix"
      assert(t1.last.contains('x'))

      assert(q"test  ".trim.last.contains('t'))
    }

    "support joinN (newline join)" in {
      val trees = Seq(q"line1", q"line2", q"line3")
      val joined = trees.joinN()
      assert(joined.render == "line1\nline2\nline3")

      // Empty sequence
      assert(Seq.empty[TextTree[Int]].joinN().mapRender(_.toString) == "")

      // Single element
      assert(Seq(q"single").joinN().render == "single")
    }

    "support joinNN (double newline join)" in {
      val trees = Seq(q"para1", q"para2", q"para3")
      val joined = trees.joinNN()
      assert(joined.render == "para1\n\npara2\n\npara3")

      // Empty sequence
      assert(Seq.empty[TextTree[Int]].joinNN().mapRender(_.toString) == "")
    }

    "handle empty sequences in join operations" in {
      val empty = Seq.empty[TextTree[Int]]
      assert(empty.join(":").mapRender(_.toString) == "")
      // join with shift applies default shift of 2, even to empty content
      assert(empty.join("{\n", ",", "\n}").mapRender(_.toString) == "{\n  \n}")
      // With no shift, we get the expected result
      assert(empty.join("{\n", ",", "\n}", None).mapRender(_.toString) == "{\n\n}")
    }

    "handle single element in join operations" in {
      val single = Seq(q"solo")
      assert(single.join(":").render == "solo")
      assert(single.join("{\n", ",", "\n}").render == "{\n  solo\n}")
    }

    "handle complex nested structures" in {
      val inner1 = q"${TestVal("a")} ${TestVal("b")}"
      val inner2 = q"${TestVal("c")} ${TestVal("d")}"
      val outer = q"start $inner1 middle $inner2 end"

      assert(outer.values.length == 4)
      assert(outer.dump == "start TestVal(a) TestVal(b) middle TestVal(c) TestVal(d) end")
    }

    "handle shift with empty trees" in {
      val empty = q""
      val shifted = empty.shift(5)
      // Shift node itself propagates isEmpty
      assert(shifted.isEmpty)
      // But when rendered, shift still adds spaces even to empty content
      assert(shifted.render == "     ")
    }

    "handle trim with empty trees" in {
      val empty = q""
      val trimmed = empty.trim
      assert(trimmed.isEmpty)
    }

    "preserve types through operations" in {
      val t1: TextTree[TestVal] = q"${TestVal("1")}"
      val t2: TextTree[TestVal] = t1.shift(2)
      val t3: TextTree[TestVal] = t2.trim
      val t4: TextTree[TestVal] = Seq(t1, t3).join(",")

      assert(t4.values.forall(_.isInstanceOf[TestVal]))
    }

    "support map transformation preserving structure" in {
      val t = q"a ${TestVal("1")} b ${TestVal("2")}"
      val mapped = t.map(v => TestVal2(v.value + "!"))

      assert(mapped.dump == "a TestVal2(1!) b TestVal2(2!)")
      assert(mapped.values.forall(_.isInstanceOf[TestVal2]))
    }

    "handle stripMargin with different margin chars" in {
      val t = q""" test
                 # line1
                 # line2""".stripMargin('#')
      assert(t.dump == " test\n line1\n line2")
    }

    "handle mixed node types correctly" in {
      import TextTree._

      val t1 = text[Int]("plain")
      val t2 = value[Int](42)
      val t3 = verbatim[Int]("literal\\n")

      val combined = q"$t1 $t2 $t3"
      assert(combined.dump == "plain 42 literal\\n")
    }

    "support C-style operations" in {
      import TextTree.style.c._

      val stmt1 = q"return x"
      val stmt2 = q"if (x) { return }"

      assert(stmt1.endC().dump == "return x;")
      assert(stmt2.endC().dump == "if (x) { return }") // No semicolon after }

      val stmts = Seq(q"int x = 1", q"int y = 2", q"if (true) { }")
      val joined = stmts.joinCN()
      assert(joined.render == "int x = 1;\nint y = 2;\nif (true) { }")
    }

    "handle deeply nested shifts and trims" in {
      val base = q"test"
      val complex = base.shift(2).trim.shift(1).trim
      val rendered = complex.render

      // Multiple trims and shifts should compose
      assert(rendered.nonEmpty)
    }

    "verify flatMap behavior" in {
      val t1 = q"${TestVal("1")}"
      val t2 = q"test"
      val nested = q"$t1 $t2"

      val flat1 = nested.flatten
      val flat2 = flat1.flatten

      // Multiple flattens should be idempotent
      assert(flat1.dump == flat2.dump)
      assert(flat1.dump == nested.dump)
    }

    "handle isEmpty with nested empty trees" in {
      val e1 = q""
      val e2 = q"$e1"
      // e3 has a space between e1 and e2 in the interpolation, so it's not empty
      val e3 = q"$e1$e2" // No space between interpolations

      assert(e1.isEmpty)
      assert(e2.isEmpty)
      assert(e3.isEmpty)

      // Shift and trim of empty trees are considered empty
      assert(e1.shift(5).isEmpty)
      assert(e1.trim.isEmpty)
    }

    "handle string interpolations properly" in {
      val str = "embedded"
      val t = q"test $str value"
      assert(t.dump == "test embedded value")

      // String in TextTree should be treated as StringNode
      val t2: TextTree[Nothing] = q"$str"
      assert(t2.dump == "embedded")
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

    object conversion {
      implicit def arg_from_sub[T](sub: T)(implicit conv: T => Wrap): InterpolationArg[Wrap] = new InterpolationArg[Wrap] {
        override def asNode: TextTree[Wrap] = ValueNode[Wrap](conv(sub))
      }
    }
  }

}
