package com.github.pshirshov.izumi.idealingua.il.parser

import com.github.pshirshov.izumi.idealingua.il.parser.structure._
import org.scalatest.WordSpec


class BasicParserTest
  extends WordSpec with ParserTestTools {

  "IL parser" should {
    "parse docstrings" in {
      assertParses(comments.DocComment,
        """/** docstring
          | */""".stripMargin)

      assertParses(comments.DocComment,
        """/** docstring
          |  * docstring
          |  */""".stripMargin)

      assertParses(comments.DocComment,
        """/** docstring
          |* docstring
          |*/""".stripMargin)

      assertParses(comments.DocComment,
        """/**
          |* docstring
          |*/""".stripMargin)

      assertParses(comments.DocComment,
        """/**
          |* docstring
          |*
          |*/""".stripMargin)

      assertParsesInto(comments.DocComment,
        """/** docstring
          |  * with *stars*
          |  */""".stripMargin,
        """ docstring
          | with *stars*""".stripMargin
      )
    }

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

  }
}
