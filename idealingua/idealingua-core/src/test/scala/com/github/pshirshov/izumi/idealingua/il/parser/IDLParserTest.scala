package com.github.pshirshov.izumi.idealingua.il.parser

import com.github.pshirshov.izumi.idealingua.il.parser.structure._
import fastparse.all._
import fastparse.core.Parsed
import org.scalatest.WordSpec


class IDLParserTest extends WordSpec {

  "IL parser" should {
    "parse imports" in {
      assertParses(DefDomain.importBlock, "import a.b.c")
      assertParses(DefDomain.importBlock, "import     a.b.c")
      assertParses(DefDomain.importBlock, "import a")
    }

    "parse domain declaration" in {
      assertParses(DefDomain.domainBlock, "domain x.y.z")
    }

    "parse aliases" in {
      assertParses(DefMember.aliasBlock, "alias x = y")
    }

    "parse enums" in {
      assertParses(DefMember.enumBlock, "enum MyEnum {X Y Zz}")
      assertParses(DefMember.enumBlock, "enum MyEnum { X Y Z }")
      assertParses(DefMember.enumBlock, "enum MyEnum {  X  Y  Z  }")
      assertParses(DefMember.enumBlock,
        """enum MyEnum {
          |X
          | Y
          |Z
          |}""".stripMargin)
      assertParses(DefMember.enumBlock, "enum MyEnum {X,Y,Z}")
      assertParses(DefMember.enumBlock, "enum MyEnum {X|Y|Z}")
      assertParses(DefMember.enumBlock, "enum MyEnum { X|Y|Z }")
      assertParses(DefMember.enumBlock, "enum MyEnum {X | Y | Z}")
      assertParses(DefMember.enumBlock, "enum MyEnum { X | Y | Z }")
      assertParses(DefMember.enumBlock, "enum MyEnum { X , Y , Z }")
      assertParses(DefMember.enumBlock, "enum MyEnum = X | Y | Z")
      assertParses(DefMember.enumBlock, "enum MyEnum = X | /**/ Y | Z")
      assertParses(DefMember.enumBlock,
        """enum MyEnum = X
          ||Y
          || Z""".stripMargin)
      assertParses(DefMember.enumBlock,
        """enum MyEnum =
          || X
          | | Y
          || Z""".stripMargin)
    }

    "parse empty blocks" in {
      assertParses(DefMember.mixinBlock, "mixin Mixin {}")
      assertParses(DefMember.idBlock, "id Id {}")
      assertParses(DefMember.serviceBlock, "service Service {}")
      assertParses(DefMember.dtoBlock, "data Data {}")
    }

    "parse dto blocks" in {
      assertParses(DefMember.dtoBlock,
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
      assertParses(sep.any,
        """// test
          |/*test*/
          | /* test/**/*/
        """.stripMargin)
      assertParses(comments.ShortComment, "// test\n")
      assertParses(comments.ShortComment, "//\n")
      assertParses(sep.any, "//\n")
      assertParses(sep.any, "// test\n")

    }

    "parse service defintions" in {
      assertParses(DefService.inlineStruct, "(a: A, b: B, + C)")
      assertParses(DefService.inlineStruct, "(a: str)")
      assertParses(DefService.inlineStruct, "(+ A)")
      assertParses(DefService.adtOut, "( A \n | \n B )")
      assertParses(DefService.adtOut, "(A|B)")
      assertParses(DefService.adtOut, "(A | B)")
      assertParses(DefService.inlineStruct, "(\n  firstName: str \n , \n secondName: str\n)")
    }

    "parse identifiers" in {
      assertParses(ids.domainId, "x.y.z")
      assertParses(ids.identifier, "x.y#z")
    }

    "parse fields" in {
      assertParses(DefStructure.field, "a: str")
      assertParses(DefStructure.field, "a: domain#Type")
      assertParses(DefStructure.field, "a: map[str, str]")
      assertParses(DefStructure.field, "a: map[str, set[domain#Type]]")
    }

    "parse adt members" in {
      assertParses(DefStructure.adtMember, "X")
      assertParses(DefStructure.adtMember, "X as T")

      assertParses(DefStructure.adtMember, "a.b.c#X")
      assertParses(DefStructure.adtMember, "a.b.c#X as T")
    }

    "parse adt blocks" in {
      assertParses(DefMember.adtBlock, "adt MyAdt { X as XXX | Y }")
      assertParses(DefMember.adtBlock, "adt MyAdt { X | Y | a.b.c#D as B }")
      assertParses(DefMember.adtBlock, "adt MyAdt = X | Y | Z")
      assertParses(DefMember.adtBlock, "adt MyAdt { X }")
      assertParses(DefMember.adtBlock, "adt MyAdt {a.b.c#D}")
      assertParses(DefMember.adtBlock, "adt MyAdt { a.b.c#D }")
      assertParses(DefMember.adtBlock, "adt MyAdt { a.b.c#D Z Z }")
      assertParses(DefMember.adtBlock, "adt MyAdt { a.b.c#D  Z  Z }")
      assertParses(DefMember.adtBlock, "adt MyAdt { X Y a.b.c#D Z }")
      assertParses(DefMember.adtBlock, "adt MyAdt { X Y a.b.c#D }")
      assertParses(DefMember.adtBlock,
        """adt MyAdt {
          | X
          |Y
          | a.b.c#D
          |}""".stripMargin)
    }

    "parse service definition" in {
      assertParses(DefService.method, "def greetAlgebraicOut(firstName: str, secondName: str) => ( SuccessData | ErrorData )")
      assertParseableCompletely(DefService.methods,
        """def greetAlgebraicOut(firstName: str, secondName: str) => ( SuccessData | ErrorData )
          |def greetAlgebraicOut(firstName: str, secondName: str) => ( SuccessData | ErrorData )""".stripMargin)

      assertParseableCompletely(DefMember.serviceBlock,
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

      val badenums =
        """domain idltest.enums
          |
          |enum ShortSyntaxEnum = Element1 | Element2
          |
          |
          |data SomeGenerics {
          |  test: map[TestEnum, TestEnum]
          |}
          |
        """.stripMargin

      assertDomainParses(badenums)
    }
  }

  private def assertParses[T](p: Parser[T], str: String): T = {
    assertParseableCompletely(p, str)
    assertParseable(p, str)
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
    val parsed = assertParseable(IDLParser.fullDomainDef, str)
    assert(parsed.model.definitions.nonEmpty)
    ()
  }

}
