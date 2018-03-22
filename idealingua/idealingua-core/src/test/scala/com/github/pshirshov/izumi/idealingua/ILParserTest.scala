package com.github.pshirshov.izumi.idealingua

import com.github.pshirshov.izumi.idealingua.il.ILParser
import fastparse.all._
import fastparse.core.Parsed
import org.scalatest.WordSpec


class ILParserTest extends WordSpec {
  private def assertParses[T](p: Parser[T], str: String): T = {
    p.parse(str) match {
      case Parsed.Success(v, _) =>
        v
      case Parsed.Failure(lp, idx, e) =>
        throw new IllegalStateException(s"Parsing failed: $lp, $idx, $e, ${e.traced}, ${e.traced.trace}")
    }
  }

  private def assertDomainParses(str: String): Unit = {
    val parsed = assertParses(new ILParser().fullDomainDef, str)
    assert(parsed.domain.types.nonEmpty)
  }

  "IL parser" should {
    "parse domain definition" in {
      assertParses(new ILParser().identifier, "x.y.z")
      assertParses(new ILParser().domainId, "domain x.y.z")
      assertParses(new ILParser().field, "a: map[str, str]")
      assertParses(new ILParser().field, "a: map[str, set[x#Y]]")

      assertParses(new ILParser().aliasBlock, "alias x = y")
      assertParses(new ILParser().enumBlock, "enum MyEnum {X Y Zz}")
      assertParses(new ILParser().adtBlock, "adt MyAdt { X Y a.b.c#D }")

      assertParses(new ILParser().mixinBlock, "mixin Mixin {}")
      assertParses(new ILParser().dtoBlock, "data Data {}")
      assertParses(new ILParser().idBlock, "id Id {}")
      assertParses(new ILParser().serviceBlock, "service Service {}")
      assertParses(new ILParser().SepLineOpt, "// test")
      assertParses(new ILParser().SepLineOpt,
        """// test
          |/*test*/
          | /* test/**/*/
        """.stripMargin)


      val domaindef1 =
        """domain x.y.z
          |
          |alias x = y
          |/**/
          |""".stripMargin
      assertDomainParses(domaindef1)

      val domaindef =
        """domain x.y.z
          |/*test*/
          |import x.domain/*test*/
          |import x.y.domain
          |
          |include "b/c/d.model"/*test*/
          |include "a.model"
          |
          |alias x = str
          |/*test*/
          |alias B = str/*test*/
          |
          |adt AnAdt {
          |  AllTypes
          |  TestObject
          |}
          |
          |enum MyEnum {X Y Zz}
          |/*test*/
          |mixin Mixin {/*test*/
          | + Mixin
          | a: B
          | /*test*/
          | c: x#Y
          |}
          |
          |data Data {
          |// test
          |+ Mixin//test
          |// test
          |+Mixin
          |/**/
          |}
          |
          |id Id0 {
          |}
          |
          |id Id1 {
          |
          |}
          |
          |id Id2 {
          |//test
          |/**/
          |}
          |
          |
          |id/*test*/Id/*test*/{
          |  a:/*test*/B/*test*/
          |  b: map[str, set[x#Y]]
          | /*test*/
          | // test
          |}
          |service Service {
          | /*test*/
          | /*test*/
          | // test
          | def/*test*/testMethod(Mixin1, c.d#Mixin2, x.y#Mixin3, x.y.z#Mixin4, z#Mixin5): (Mixin1, a.b#Mixin3)
          | // test
          | /*test*/
          | def testMethod1(Mixin1,/*test*/c.d#Mixin2, x.y#Mixin3, x.y.z#Mixin4, z#Mixin5): (Mixin1, a.b#Mixin3)
          |}
          |
          |// test
          |""".stripMargin

      assertDomainParses(domaindef)


    }
  }
}
