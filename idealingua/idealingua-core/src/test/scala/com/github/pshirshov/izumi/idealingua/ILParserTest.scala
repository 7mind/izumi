package com.github.pshirshov.izumi.idealingua

import com.github.pshirshov.izumi.idealingua.il.parser.ILParser
import fastparse.all._
import fastparse.core.Parsed
import org.scalatest.WordSpec


class ILParserTest extends WordSpec {
  private val parser = new ILParser()

  "IL parser" should {
    "parse imports" in {
      assertParses(parser.domains.importBlock, "import a.b.c")
      assertParses(parser.domains.importBlock, "import     a.b.c")
      assertParses(parser.domains.importBlock, "import a")
    }

    "parse aliases" in {
      assertParses(parser.blocks.aliasBlock, "alias x = y")
    }

    "parse enums" in {
      assertParses(parser.blocks.enumBlock, "enum MyEnum {X Y Zz}")
      assertParses(parser.blocks.enumBlock, "enum MyEnum { X Y Z }")
      assertParses(parser.blocks.enumBlock, "enum MyEnum {  X  Y  Z  }")
      assertParses(parser.blocks.enumBlock, "enum MyEnum = X | Y | Z")
      assertParses(parser.blocks.enumBlock,
        """enum MyEnum {
          |X
          | Y
          |Z
          |}""".stripMargin)
      assertParses(parser.blocks.enumBlock, "enum MyEnum {X,Y,Z}")
      assertParses(parser.blocks.enumBlock, "enum MyEnum {X|Y|Z}")
      assertParses(parser.blocks.enumBlock, "enum MyEnum { X | Y | Z }")
      assertParses(parser.blocks.enumBlock, "enum MyEnum { X , Y , Z }")
    }

    "parse empty blocks" in {
      assertParses(parser.blocks.mixinBlock, "mixin Mixin {}")
      assertParses(parser.blocks.idBlock, "id Id {}")
      assertParses(parser.blocks.serviceBlock, "service Service {}")
      assertParses(parser.blocks.dtoBlock, "data Data {}")
    }

    "parse dto blocks" in {
      assertParses(parser.blocks.dtoBlock,
        """data Data {
          |& Add
          |&&& Add
          |+ Embed
          |+++ Embed
          |- Remove
          |--- Remove
          |field: F
          |... Embed
          |another: F
          |}""".stripMargin)
    }

    "parse complex comments" in {
      assertParses(parser.sep.any,
        """// test
          |/*test*/
          | /* test/**/*/
        """.stripMargin)
      assertParses(parser.comments.ShortComment, "// test\n")
      assertParses(parser.comments.ShortComment, "//\n")
      assertParses(parser.sep.any, "//\n")
      assertParses(parser.sep.any, "// test\n")

    }

    "parse service defintions" in {
      assertParses(parser.services.inlineStruct, "(a: A, b: B, + C)")
      assertParses(parser.services.inlineStruct, "(a: str)")
      assertParses(parser.services.inlineStruct, "(+ A)")
      assertParses(parser.services.adtOut, "( A \n | \n B )")
      assertParses(parser.services.adtOut, "(A|B)")
      assertParses(parser.services.adtOut, "(A | B)")
      assertParses(parser.services.inlineStruct, "(\n  firstName: str \n , \n secondName: str\n)")
    }

    "parse identifiers" in {
      assertParses(parser.domains.domainBlock, "domain x.y.z")
      assertParses(parser.domains.domainId, "x.y.z")
      assertParses(parser.ids.identifier, "x.y#z")
    }

    "parse fields" in {
      assertParses(parser.defs.field, "a: str")
      assertParses(parser.defs.field, "a: domain#Type")
      assertParses(parser.defs.field, "a: map[str, str]")
      assertParses(parser.defs.field, "a: map[str, set[domain#Type]]")
    }

    "parse adt members" in {
      assertParses(parser.defs.adtMember, "X")
      assertParses(parser.defs.adtMember, "X as T")

      assertParses(parser.defs.adtMember, "a.b.c#X")
      assertParses(parser.defs.adtMember, "a.b.c#X as T")
    }

    "parse adt blocks" in {
      assertParses(parser.blocks.adtBlock, "adt MyAdt { X as XXX | Y }")
      assertParses(parser.blocks.adtBlock, "adt MyAdt { X | Y | a.b.c#D as B }")
      assertParses(parser.blocks.adtBlock, "adt MyAdt = X | Y | Z")
      assertParses(parser.blocks.adtBlock, "adt MyAdt { X }")
      assertParses(parser.blocks.adtBlock, "adt MyAdt {a.b.c#D}")
      assertParses(parser.blocks.adtBlock, "adt MyAdt { a.b.c#D }")
      assertParses(parser.blocks.adtBlock, "adt MyAdt { a.b.c#D Z Z }")
      assertParses(parser.blocks.adtBlock, "adt MyAdt { a.b.c#D  Z  Z }")
      assertParses(parser.blocks.adtBlock, "adt MyAdt { X Y a.b.c#D Z }")
      assertParses(parser.blocks.adtBlock, "adt MyAdt { X Y a.b.c#D }")
      assertParses(parser.blocks.adtBlock,
        """adt MyAdt {
          | X
          |Y
          | a.b.c#D
          |}""".stripMargin)
    }

    "parse service definition" in {
      assertParses(parser.services.method, "def greetAlgebraicOut(firstName: str, secondName: str) => ( SuccessData | ErrorData )")
      assertParseableCompletely(parser.services.methods,
        """def greetAlgebraicOut(firstName: str, secondName: str) => ( SuccessData | ErrorData )
          |def greetAlgebraicOut(firstName: str, secondName: str) => ( SuccessData | ErrorData )""".stripMargin)

      assertParseableCompletely(parser.blocks.serviceBlock,
        """service FileService {
          |  def greetAlgebraicOut(firstName: str, secondName: str) => ( SuccessData | ErrorData )
          |}""".stripMargin)
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
          | def/*test*/testMethod(+Mixin1, +c.d#Mixin2, +x.y#Mixin3, +x.y.z#Mixin4, +z#Mixin5, a: str): (+Mixin1, +a.b#Mixin3, b: str)
          | // test
          | /*test*/
          | def testMethod1(+Mixin1,/*test*/+c.d#Mixin2, +x.y#Mixin3, +x.y.z#Mixin4, +z#Mixin5, a: str): (A | B)
          | def testMethod2(a: list[str]): list[str]
          |}
          |
          |// test
          |""".stripMargin

      assertDomainParses(domaindef)
    }
  }

  private def assertParses[T](p: Parser[T], str: String): T = {
    assertParseable(p, str)
    assertParseableCompletely(p, str)
  }

  private def assertParseableCompletely[T](p: Parser[T], str: String): T = {
    assertParseable(p ~ End, str)
  }

  private def assertParseable[T](p: Parser[T], str: String): T = {
    p.parse(str) match {
      case Parsed.Success(v, index) =>
        assert(index == str.length, s"Seems like value wasn't parsed completely: $v")
        v
      case Parsed.Failure(lp, idx, e) =>
        throw new IllegalStateException(s"Parsing failed: $lp, $idx, $e, ${e.traced}, ${e.traced.trace}")
    }

  }


  private def assertDomainParses(str: String): Unit = {
    val parsed = assertParseable(parser.fullDomainDef, str)
    assert(parsed.model.definitions.nonEmpty)
    ()
  }

}
