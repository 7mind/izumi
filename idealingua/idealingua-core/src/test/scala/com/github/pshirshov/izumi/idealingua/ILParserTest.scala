package com.github.pshirshov.izumi.idealingua

import com.github.pshirshov.izumi.idealingua.il.parser.ILParser
import fastparse.all._
import fastparse.core.Parsed
import org.scalatest.WordSpec


class ILParserTest extends WordSpec {
  private val parser = new ILParser()

  "IL parser" should {
    "parse all test domains" in {
      val defs = IDLTestTools.loadDefs()
      assert(defs.nonEmpty)
      defs.foreach {
        d =>
          assert(d.types.nonEmpty)
      }
    }

    "parse basic contstructs" in {
      assertParses(parser.Separators.SepLineOpt, "// test")
      assertParses(parser.Separators.SepLineOpt,
        """// test
          |/*test*/
          | /* test/**/*/
        """.stripMargin)

      assertParses(parser.identifier, "x.y.z")
      assertParses(parser.field, "a: map[str, str]")
      assertParses(parser.field, "a: map[str, set[x#Y]]")
      assertParses(parser.services.inlineStruct, "(a: A, b: B, +C)")
      assertParses(parser.services.inlineStruct, "(a: str)")
      assertParses(parser.services.adtOut, "( A \n | \n B )")
      assertParses(parser.services.adtOut, "(A|B)")
      assertParses(parser.services.adtOut, "(A | B)")
      assertParses(parser.services.inlineStruct, "(\n  firstName: str \n , \n secondName: str\n  )")

      assertParses(parser.blocks.aliasBlock, "alias x = y")
      assertParses(parser.blocks.enumBlock, "enum MyEnum {X Y Zz}")
      assertParses(parser.blocks.adtBlock, "adt MyAdt { X Y a.b.c#D }")
      assertParses(parser.blocks.mixinBlock, "mixin Mixin {}")
      assertParses(parser.blocks.dtoBlock, "data Data {}")
      assertParses(parser.blocks.dtoBlock,
        """data Data {
          |+ Add
          |+++ Add
          |* Embed
          |field: F
          |... Embed
          |another: F
          |}""".stripMargin)
      assertParses(parser.blocks.idBlock, "id Id {}")
      assertParses(parser.blocks.serviceBlock, "service Service {}")

      assertParses(parser.domains.domainId, "domain x.y.z")
    }

    "parse domain definition" in {
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

  private def assertParses[T](p: Parser[T], str: String): T = {
    p.parse(str) match {
      case Parsed.Success(v, _) =>
        v
      case Parsed.Failure(lp, idx, e) =>
        throw new IllegalStateException(s"Parsing failed: $lp, $idx, $e, ${e.traced}, ${e.traced.trace}")
    }
  }


  private def assertDomainParses(str: String): Unit = {
    val parsed = assertParses(parser.fullDomainDef, str)
    assert(parsed.model.definitions.nonEmpty)
    ()
  }

}
