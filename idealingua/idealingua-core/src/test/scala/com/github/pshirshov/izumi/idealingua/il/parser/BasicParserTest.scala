package com.github.pshirshov.izumi.idealingua.il.parser

import com.github.pshirshov.izumi.idealingua.il.parser.structure._
import org.scalatest.WordSpec


class BasicParserTest
  extends WordSpec with ParserTestTools {

  "IL parser" should {
    "parse annos" in {
      assertParses(DefConst.defAnno,
        """@TestAnno[]""".stripMargin)

      assertParses(DefConst.defAnno,
        """@TestAnno[a=1]""".stripMargin)

      assertParses(DefConst.defAnno,
        """@TestAnno[a=1, b="xxx"]""".stripMargin)
    }
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
      assertParses(DefStructure.aliasBlock, "alias x = y")
    }

    "parse enums" in {
      assertParses(DefStructure.enumBlock, "enum MyEnum {X Y Zz}")
      assertParses(DefStructure.enumBlock, "enum MyEnum { X Y Z }")
      assertParses(DefStructure.enumBlock, "enum MyEnum {  X  Y  Z  }")
      assertParses(DefStructure.enumBlock,
        """enum MyEnum {
          |X
          | Y
          |Z
          |}""".stripMargin)
      assertParses(DefStructure.enumBlock, "enum MyEnum {X,Y,Z}")
      assertParses(DefStructure.enumBlock, "enum MyEnum {X|Y|Z}")
      assertParses(DefStructure.enumBlock, "enum MyEnum { X|Y|Z }")
      assertParses(DefStructure.enumBlock, "enum MyEnum {X | Y | Z}")
      assertParses(DefStructure.enumBlock, "enum MyEnum { X | Y | Z }")
      assertParses(DefStructure.enumBlock, "enum MyEnum { X , Y , Z }")
      assertParses(DefStructure.enumBlock, "enum MyEnum = X | Y | Z")
      assertParses(DefStructure.enumBlock, "enum MyEnum = X | /**/ Y | Z")
      assertParses(DefStructure.enumBlock,
        """enum MyEnum = X
          ||Y
          || Z""".stripMargin)
      assertParses(DefStructure.enumBlock,
        """enum MyEnum =
          || X
          | | Y
          || Z""".stripMargin)
    }

    "parse empty blocks" in {
      assertParses(DefStructure.mixinBlock, "mixin Mixin {}")
      assertParses(DefStructure.idBlock, "id Id {}")
      assertParses(DefService.serviceBlock, "service Service {}")
      assertParses(DefStructure.dtoBlock, "data Data {}")
    }

    "parse dto blocks" in {
      assertParses(DefStructure.dtoBlock,
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
      assertParses(DefStructure.inlineStruct, "(a: A, b: B, + C)")
      assertParses(DefStructure.inlineStruct, "(a: str)")
      assertParses(DefStructure.inlineStruct, "(+ A)")
      assertParses(DefStructure.adtOut, "( A \n | \n B )")
      assertParses(DefStructure.adtOut, "(A|B)")
      assertParses(DefStructure.adtOut, "(A | B)")
      assertParses(DefStructure.inlineStruct, "(\n  firstName: str \n , \n secondName: str\n)")
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
      assertParses(DefStructure.adtBlock, "adt MyAdt { X as XXX | Y }")
      assertParses(DefStructure.adtBlock, "adt MyAdt { X | Y | a.b.c#D as B }")
      assertParses(DefStructure.adtBlock, "adt MyAdt = X | Y | Z")
      assertParses(DefStructure.adtBlock, "adt MyAdt { X }")
      assertParses(DefStructure.adtBlock, "adt MyAdt {a.b.c#D}")
      assertParses(DefStructure.adtBlock, "adt MyAdt { a.b.c#D }")
      assertParses(DefStructure.adtBlock, "adt MyAdt { a.b.c#D Z Z }")
      assertParses(DefStructure.adtBlock, "adt MyAdt { a.b.c#D  Z  Z }")
      assertParses(DefStructure.adtBlock, "adt MyAdt { X Y a.b.c#D Z }")
      assertParses(DefStructure.adtBlock, "adt MyAdt { X Y a.b.c#D }")
      assertParses(DefStructure.adtBlock,
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

      assertParseableCompletely(DefService.serviceBlock,
        """service FileService {
          |  def greetAlgebraicOut(firstName: str, secondName: str) => ( SuccessData | ErrorData )
          |}""".stripMargin)
    }

  }
}
