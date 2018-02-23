package com.github.pshirshov.izumi.idealingua

import com.github.pshirshov.izumi.idealingua.il.ILParser
import fastparse.core.Parsed
import org.scalatest.WordSpec


class ILParTest extends WordSpec {
  "IL parser" should {
    "parse domain definition" in {
      println(new ILParser().identifier.parse("x.y.z"))
      println(new ILParser().domainId.parse("domain x.y.z"))
      println(new ILParser().aliasBlock.parse("alias x = y"))
      println(new ILParser().enumBlock.parse("enum MyEnum {X Y Zz}"))


      val domaindef =
        """domain x.y.z
          |alias x = y
          |enum MyEnum {X Y Zz}
        """.stripMargin
      new ILParser().expr.parse(domaindef) match {
        case Parsed.Success(v, i) =>
          println(v)
        case Parsed.Failure(lp, idx, e) =>
          println(lp, idx, e.traced.traceParsers)
      }
    }
  }
}
