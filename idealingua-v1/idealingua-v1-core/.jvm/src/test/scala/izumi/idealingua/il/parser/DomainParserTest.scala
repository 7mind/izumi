package izumi.idealingua.il.parser

import org.scalatest.wordspec.AnyWordSpec


class DomainParserTest
  extends AnyWordSpec with ParserTestTools {

  "Domain parser" should {
    "parse domain definition" in {
      val domaindef1 =
        """domain x.y.z
          |
          |alias x = y
          |/**/
          |""".stripMargin
      assertDomainParses(domaindef1)

      val domaindef =
        """/** kind of a domain
          |  */
          |@Pragma(nonportable=true)
          |domain x.y.z
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
          |adt NestedAdt0 = | enum NestedAdt0Option1 { VAL1,  VAL2}
          |                 | enum NestedAdt0Option2 { VAL1,  VAL2}
          |
          |/** docstring
          |  */
          |@TestAnno()
          |adt NestedAdt1 {
          |  /** docstring
          |    */
          |  @TestAnno()
          |  enum NestedAdt1Option1 {
          |    VAL1
          |    VAL2
          |  }
          |
          |  /** docstring
          |  */
          |  @TestAnno()
          |  data NestedAdt1Option2 {
          |  }
          |}
          |
          |/** docstring
          |  */
          |@TestAnno()
          |adt NestedAdt2 = | /** docstring
          |                    */
          |                   @TestAnno()
          |                   enum NestedAdt2Option1 {
          |                     VAL11
          |                     VAL12
          |                   }
          |                 | /** docstring
          |                    */
          |                   @TestAnno()
          |                   enum NestedAdt2Option2 {
          |                     VAL21
          |                     VAL22
          |                   }
          |
          |mixin Test {
          |  field: JavaMap[int, str]
          |}
          |
          |foreign JavaTime {
          |  "scala": t"java.time.LocalDateTime"
          |}
          |
          |foreign JavaMap[KEY, VALUE] {
          |  "java": t"java.util.Map<${KEY}, ${VALUE}>"
          |}
          |
          |foreign JavaMap[A, B] {
          |  "java"
          |  : t"java.util.Map<${A}, ${B}>"
          |}
          |
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
          | def/*test*/testMethod(+Mixin1, +c.d#Mixin2, +x.y#Mixin3, +x.y.z#Mixin4, +z#Mixin5, a: str,): (+Mixin1, +a.b#Mixin3, b: str)
          | // test
          | /*test*/
          | def testMethod1(+Mixin1,/*test*/+c.d#Mixin2, +x.y#Mixin3, +x.y.z#Mixin4, +z#Mixin5, a: str,): (A | B)
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


}

