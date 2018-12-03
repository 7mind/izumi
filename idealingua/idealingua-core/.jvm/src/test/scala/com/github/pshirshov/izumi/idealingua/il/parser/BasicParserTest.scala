package com.github.pshirshov.izumi.idealingua.il.parser

import com.github.pshirshov.izumi.idealingua.il.parser.structure._
import org.scalatest.WordSpec

class BasicParserTest
  extends WordSpec with ParserTestTools {

  "IL parser" should {
    "parse annos" in {

      assertParses(DefConst.defAnno(_),
        """@TestAnno()""".stripMargin)

      assertParses(DefConst.defAnno(_),
        """@TestAnno(a=1)""".stripMargin)

      assertParses(DefConst.defAnno(_),
        """@TestAnno(a=1, b="xxx",c=true,d=false,e=[1,2,"x"],f={a=1,b="str"})""".stripMargin)

      assertParses(DefConst.defAnno(_),
        """@TestAnno(a=1, /*comment*/ b="xxx")""".stripMargin)
    }

    "parse imports" in {
      assertParses(DefDomain.importBlock(_), "import a.b.c")
      assertParses(DefDomain.importBlock(_), "import     a.b.c")
      assertParses(DefDomain.importBlock(_), "import a.b.{c, d}")
      assertParses(DefDomain.importBlock(_), "import a")
    }

    "parse domain declaration" in {
      assertParses(DefDomain.domainBlock(_), "domain x.y.z")
    }

    "parse aliases" in {
      assertParses(DefStructure.aliasBlock(_), "alias x = y")
    }

    "parse enclosed enums" in {
      assertParses(DefStructure.enumBlock(_), "enum MyEnum {X Y Zz}")
      assertParses(DefStructure.enumBlock(_), "enum MyEnum { X Y Z }")
      assertParses(DefStructure.enumBlock(_), "enum MyEnum {  X  Y  Z  }")
      assertParses(DefStructure.enumBlock(_),
        """enum MyEnum {
          |X
          | Y
          |Z
          |}""".stripMargin)
      assertParses(DefStructure.enumBlock(_),
        """enum MyEnum {
          |  ELEMENT1
          |  // comment
          |  ELEMENT2
          |}""".stripMargin)

      assertParses(DefStructure.enumBlock(_),
        """enum MyEnum {
          |  ELEMENT1
          |  // comment
          |  /* comment 2*/
          |  ELEMENT2
          |}""".stripMargin)
      assertParses(DefStructure.enumBlock(_),
        """enum MyEnum {
          |  ELEMENT1 // comment 3
          |  // comment
          |  /* comment 2*/
          |  ELEMENT2
          |}""".stripMargin)
      assertParses(DefStructure.enumBlock(_), "enum MyEnum {X,Y,Z}")
      assertParses(DefStructure.enumBlock(_), "enum MyEnum {X|Y|Z}")
      assertParses(DefStructure.enumBlock(_), "enum MyEnum { X|Y|Z }")
      assertParses(DefStructure.enumBlock(_), "enum MyEnum {X | Y | Z}")
      assertParses(DefStructure.enumBlock(_), "enum MyEnum { X | Y | Z }")
      assertParses(DefStructure.enumBlock(_), "enum MyEnum { X , Y , Z }")

    }

    "parse free-form enums" in {
      assertParses(DefStructure.enumBlock(_), "enum MyEnum = X | Y | Z")
      assertParses(DefStructure.enumBlock(_), "enum MyEnum = X | /**/ Y | Z")
      assertParses(DefStructure.enumBlock(_),
        """enum MyEnum = X
          ||Y
          || Z""".stripMargin)
      assertParses(DefStructure.enumBlock(_),
        """enum MyEnum =
          || X
          | | Y
          || Z""".stripMargin)


    }

    "parse empty blocks" in {
      assertParses(DefStructure.mixinBlock(_), "mixin Mixin {}")
      assertParses(DefStructure.idBlock(_), "id Id {}")
      assertParses(DefService.serviceBlock(_), "service Service {}")
      assertParses(DefStructure.dtoBlock(_), "data Data {}")
    }

    "parse dto blocks" in {
      assertParses(DefStructure.dtoBlock(_),
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
      assertParses(sep.any(_),
        """// test
          |/*test*/
          | /* test/**/*/
        """.stripMargin)
      assertParses(comments.ShortComment(_), "// test\n")
      assertParses(comments.ShortComment(_), "//\n")
      assertParses(sep.any(_), "//\n")
      assertParses(sep.any(_), "// test\n")
    }

    "parse complex comments -2" in {
      assertParses(DefStructure.sepEnum(_), " ")
      assertParses(DefStructure.sepEnum(_), "//comment\n")
      assertParses(DefStructure.sepEnum(_), " //comment\n")
      assertParses(DefStructure.sepEnum(_), "  //comment\n  ")
      assertParses(DefStructure.sepEnum(_), "  //comment0\n  //comment1\n  ")
      assertParses(DefStructure.sepEnum(_), "  //comment0\n  //comment1\n")
    }

    "parse service defintions" in {
      assertParses(DefStructure.inlineStruct(_), "(a: A, b: B, + C)")
      assertParses(DefStructure.inlineStruct(_), "(a: str)")
      assertParses(DefStructure.inlineStruct(_), "(+ A)")
      assertParses(DefStructure.adtOut(_), "( A \n | \n B )")
      assertParses(DefStructure.adtOut(_), "(A|B)")
      assertParses(DefStructure.adtOut(_), "(A | B)")
      assertParses(DefStructure.inlineStruct(_), "(\n  firstName: str \n , \n secondName: str\n)")
    }

    "parse identifiers" in {
      assertParses(ids.domainId(_), "x.y.z")
      assertParses(ids.identifier(_), "x.y#z")
    }

    "parse fields" in {
      assertParses(DefStructure.field(_), "a: str")
      assertParses(DefStructure.field(_), "a: domain#Type")
      assertParses(DefStructure.field(_), "a: map[str, str]")
      assertParses(DefStructure.field(_), "a: map[str, set[domain#Type]]")
    }

    "parse adt members" in {
      assertParses(DefStructure.adtMember(_), "X")
      assertParses(DefStructure.adtMember(_), "X as T")

      assertParses(DefStructure.adtMember(_), "a.b.c#X")
      assertParses(DefStructure.adtMember(_), "a.b.c#X as T")
    }

    "parse adt blocks" in {
      assertParses(DefStructure.adtBlock(_), "adt MyAdt { X as XXX | Y }")
      assertParses(DefStructure.adtBlock(_), "adt MyAdt { X | Y | a.b.c#D as B }")
      assertParses(DefStructure.adtBlock(_), "adt MyAdt = X | Y | Z")
      assertParses(DefStructure.adtBlock(_), "adt MyAdt { X }")
      assertParses(DefStructure.adtBlock(_), "adt MyAdt {a.b.c#D}")
      assertParses(DefStructure.adtBlock(_), "adt MyAdt { a.b.c#D }")
      assertParses(DefStructure.adtBlock(_), "adt MyAdt { a.b.c#D  Z  Z }")
      assertParses(DefStructure.adtBlock(_), "adt MyAdt { X Y a.b.c#D Z }")
      assertParses(DefStructure.adtBlock(_), "adt MyAdt { X Y a.b.c#D }")
      assertParses(DefStructure.adtBlock(_),
        """adt MyAdt {
          | X
          |Y
          | a.b.c#D
          |}""".stripMargin)
    }

    "parse service definition" in {
      assertParses(DefService.method(_), "def greetAlgebraicOut(firstName: str, secondName: str) => ( SuccessData | ErrorData )")
      assertParseableCompletely(DefService.methods(_),
        """def greetAlgebraicOut(firstName: str, secondName: str) => ( SuccessData | ErrorData )
          |def greetAlgebraicOut(firstName: str, secondName: str) => ( SuccessData | ErrorData )""".stripMargin)

      assertParseableCompletely(DefService.serviceBlock(_),
        """service FileService {
          |  def greetAlgebraicOut(firstName: str, secondName: str) => ( SuccessData | ErrorData )
          |}""".stripMargin)
    }

  }
}
