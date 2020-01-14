package izumi.idealingua.il.parser

import izumi.idealingua.il.parser.structure._
import izumi.idealingua.model.il.ast.raw.defns.RawMethod
import org.scalatest.wordspec.AnyWordSpec
import fastparse._

class BasicParserTest
  extends AnyWordSpec with ParserTestTools {

  import ctx._

  "IL parser" should {
    "parse annos" in {
      assertParses(defConst.defAnno(_),
        """@TestAnno()""".stripMargin)

      assertParses(defConst.defAnno(_),
        """@TestAnno(a=1)""".stripMargin)

      assertParses(defConst.defAnno(_),
        """@TestAnno(a=1, b="xxx",c=true,d=false,e=[1,2,"x",],f={a=1,b="str"} ,)""".stripMargin)
      assertParses(defConst.defAnno(_),
        """@TestAnno(e=[1,2,"x",],f = ( lst[str]([1,2,3]) ) )""".stripMargin)
      assertParses(defConst.defAnno(_),
        """@AnotherAnno(a=1, b="str", c=[1,2,3], d={x=true, y=1}, e=lst[str]([1,2,3]))""".stripMargin)

      assertParses(defConst.defAnno(_),
        """@TestAnno(a=1, /*comment*/ b="xxx")""".stripMargin)
    }

    "parse imports" in {
      import defDomain._
      assertParses(importBlock(_), "import a.b.c")
      assertParses(importBlock(_), "import     a.b.c")
      assertParses(importBlock(_), "import a.b.{c, d}")
      assertParses(importBlock(_), "import a.b.{c, d,}")
      assertParses(importBlock(_), "import a.b.{c, d ,}")
      assertParses(importBlock(_), "import a")
    }

    "parse domain declaration" in {
      import defDomain._
      assertParses(domainBlock(_), "domain x.y.z")
    }

    "parse foreign type interpolations" in {
      import ids._
      assertParses(typeInterp(_), """t"java.util.Map"""")
      assertParses(typeInterp(_), """t"java.util.Map<${A}, ${B}>"""")
    }


    "parse aliases" in {
      assertParses(defStructure.aliasBlock(_), "alias x = y")
    }

    "parse enclosed enums" in {
      assertParses(defStructure.enumBlock(_), "enum MyEnum {X Y Zz}")
      assertParses(defStructure.enumBlock(_), "enum MyEnum { X Y Z }")
      assertParses(defStructure.enumBlock(_), "enum MyEnum {  X  Y  Z  }")
      assertParses(defStructure.enumBlock(_),
        """enum MyEnum {
          | + BaseEnum
          | - REMOVED_BASE_ELEMENT
          | NEW_ELEMENT
          |}""".stripMargin)
      assertParses(defStructure.enumBlock(_),
        """enum MyEnum {
          |X
          | Y
          |Z
          |}""".stripMargin)
      assertParses(defStructure.enumBlock(_),
        """enum MyEnum {
          |  ELEMENT1
          |  // comment
          |  ELEMENT2
          |}""".stripMargin)

      assertParses(defStructure.enumBlock(_),
        """enum MyEnum {
          |  ELEMENT1
          |  // comment
          |  /* comment 2*/
          |  ELEMENT2
          |}""".stripMargin)
      assertParses(defStructure.enumBlock(_),
        """enum MyEnum {
          |  ELEMENT1 // comment 3
          |  // comment
          |  /* comment 2*/
          |  ELEMENT2
          |}""".stripMargin)
      assertParses(defStructure.enumBlock(_), "enum MyEnum {X,Y,Z}")
      assertParses(defStructure.enumBlock(_), "enum MyEnum {X|Y|Z}")
      assertParses(defStructure.enumBlock(_), "enum MyEnum { X|Y|Z }")
      assertParses(defStructure.enumBlock(_), "enum MyEnum {X | Y | Z}")
      assertParses(defStructure.enumBlock(_), "enum MyEnum { X | Y | Z }")
      assertParses(defStructure.enumBlock(_), "enum MyEnum { X , Y , Z }")
      assertParses(defStructure.enumBlock(_), "enum MyEnum { X , Y , Z , F }")

    }

    "parse free-form enums" in {
      assertParses(defStructure.enumBlock(_), "enum MyEnum = X | Y | Z")
      assertParses(defStructure.enumBlock(_), "enum MyEnum = X | /**/ Y | Z")
      assertParses(defStructure.enumBlock(_),
        """enum MyEnum = X
          ||Y
          || Z""".stripMargin)
      assertParses(defStructure.enumBlock(_),
        """enum MyEnum =
          || X
          | | Y
          || Z""".stripMargin)


    }

    "parse empty blocks" in {
      assertParses(defStructure.mixinBlock(_), "mixin Mixin {}")
      assertParses(defStructure.idBlock(_), "id Id {}")
      assertParses(defService.serviceBlock(_), "service Service {}")
      assertParses(defStructure.dtoBlock(_), "data Data {}")
    }

    "parse dto blocks" in {
      assertParses(defStructure.dtoBlock(_),
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
      assertParses(defStructure.sepEnum(_), " ")
      assertParses(defStructure.sepEnum(_), "//comment\n")
      assertParses(defStructure.sepEnum(_), " //comment\n")
      assertParses(defStructure.sepEnum(_), "  //comment\n  ")
      assertParses(defStructure.sepEnum(_), "  //comment0\n  //comment1\n  ")
      assertParses(defStructure.sepEnum(_), "  //comment0\n  //comment1\n")
    }

    "parse service defintions" in {
      assertParses(defStructure.inlineStruct(_), "(a: A, b: B, + C)")
      assertParses(defStructure.inlineStruct(_), "(a: str)")
      assertParses(defStructure.inlineStruct(_), "(+ A)")
      assertParses(defStructure.adtOut(_), "( A \n | \n B )")
      assertParses(defStructure.adtOut(_), "(A|B)")
      assertParses(defStructure.adtOut(_), "(A | B)")
      assertParses(defStructure.inlineStruct(_), "(\n  firstName: str \n , \n secondName: str\n)")
    }

    "parse identifiers" in {
      assertParses(ids.domainId(_), "x.y.z")
      assertParses(ids.identifier(_), "x.y#z")
    }

    "parse fields" in {
      assertParses(defStructure.field(_), "a: str")
      assertParses(defStructure.field(_), "a: domain#Type")
      assertParses(defStructure.field(_), "a: map[str, str]")
      assertParses(defStructure.field(_), "a: map[str, set[domain#Type]]")
    }

    "parse adt members" in {
      assertParses(defStructure.adtMember(_), "X")
      assertParses(defStructure.adtMember(_), "X as T")

      assertParses(defStructure.adtMember(_), "a.b.c#X")
      assertParses(defStructure.adtMember(_), "a.b.c#X as T")
    }

    "parse adt blocks" in {
      assertParses(defStructure.adtBlock(_), "adt MyAdt { X as XXX | Y }")
      assertParses(defStructure.adtBlock(_), "adt MyAdt { X | Y | a.b.c#D as B }")
      assertParses(defStructure.adtBlock(_), "adt MyAdt = X | Y | Z")
      assertParses(defStructure.adtBlock(_), "adt MyAdt { X }")
      assertParses(defStructure.adtBlock(_), "adt MyAdt {a.b.c#D}")
      assertParses(defStructure.adtBlock(_), "adt MyAdt { a.b.c#D }")
      assertParses(defStructure.adtBlock(_), "adt MyAdt { a.b.c#D  Z  Z }")
      assertParses(defStructure.adtBlock(_), "adt MyAdt { X Y a.b.c#D Z }")
      assertParses(defStructure.adtBlock(_), "adt MyAdt { X Y a.b.c#D }")
      assertParses(defStructure.adtBlock(_),
        """adt MyAdt {
          | X
          |Y
          | a.b.c#D
          |}""".stripMargin)
    }

    "parse service definition" in {
      def defm[_: P]: P[RawMethod.RPCMethod] = defSignature.method(kw.defm)

      assertParses(defm(_), "def greetAlgebraicOut(firstName: str, secondName: str) => ( SuccessData | ErrorData )")
      assertParseableCompletely(defService.methods(_),
        """def greetAlgebraicOut(firstName: str, secondName: str) => ( SuccessData | ErrorData )
          |def greetAlgebraicOut(firstName: str, secondName: str) => ( SuccessData | ErrorData )""".stripMargin)

      assertParseableCompletely(defService.serviceBlock(_),
        """service FileService {
          |  def greetAlgebraicOut(firstName: str, secondName: str) => ( SuccessData | ErrorData )
          |}""".stripMargin)
    }

  }
}
