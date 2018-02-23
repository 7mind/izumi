package com.github.pshirshov.izumi.idealingua

import com.github.pshirshov.izumi.idealingua.il.{ILParser, ILRenderer}
import fastparse.all._
import fastparse.core.Parsed
import org.scalatest.WordSpec


class ILParTest extends WordSpec {
  private def assertParses[T](p: Parser[T], str: String): T = {
    p.parse(str) match {
      case Parsed.Success(v, _) =>
        v
      case Parsed.Failure(lp, idx, e) =>
        println(lp, idx, e, e.traced)
        println(e.traced.trace)
        throw new IllegalStateException()
    }
  }

  private def assertDomainParses(str: String): Unit = {
    val parsed = assertParses(new ILParser().fullDomainDef, str)
    val rendered = new ILRenderer(parsed.domain).render()
    assert(rendered.nonEmpty)
    println(rendered)
  }

  "IL parser" should {
    "parse domain definition" in {
      assertParses(new ILParser().identifier, "x.y.z")
      assertParses(new ILParser().domainId, "domain x.y.z")
      assertParses(new ILParser().field, "a: map")
      assertParses(new ILParser().field, "a: map[str, str]")
      assertParses(new ILParser().field, "a: map[str, set[x#Y]]")

      assertParses(new ILParser().aliasBlock, "alias x = y")
      assertParses(new ILParser().enumBlock, "enum MyEnum {X Y Zz}")

      assertParses(new ILParser().mixinBlock, "mixin Mixin {}")
      assertParses(new ILParser().dtoBlock, "data Data {}")
      assertParses(new ILParser().idBlock, "id Id {}")
      assertParses(new ILParser().serviceBlock, "service Service {}")


      val domaindef =
        """domain x.y.z
          |
          |import "x.domain"
          |import "y/x.domain"
          |
          |include "b/c/d.model"
          |include "a.model"
          |
          |alias x = y
          |
          |enum MyEnum {X Y Zz}
          |
          |mixin Mixin {
          | + Mixin
          | a: B
          | c: x#Y
          |}
          |
          |data Data {
          |+ Mixin
          |+Mixin
          |}
          |
          |id Id {
          |  a: B
          |  b: map[str, set[x#Y]]
          |}
          |service Service {
          | def testMethod(Mixin1, c.d#Mixin2, x.y#Mixin3, x.y.z#Mixin4, z#Mixin5): (Mixin1, a.b#Mixin3)
          |}
          |""".stripMargin

      assertDomainParses(domaindef)

      val domaindef1 =
        """domain x.y.z
          |
          |alias x = y
          |""".stripMargin
      assertDomainParses(domaindef1)
    }
  }
}
