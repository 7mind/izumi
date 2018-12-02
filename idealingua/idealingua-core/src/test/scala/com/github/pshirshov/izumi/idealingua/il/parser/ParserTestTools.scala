package com.github.pshirshov.izumi.idealingua.il.parser

import fastparse._
import NoWhitespace._

trait ParserTestTools {
  def assertParsesInto[T](p: P[_] => P[T], src: String, expected: T): Unit = {
    assert(assertParses(p, src) == expected)
  }

  def assertParses[T](p: P[_] => P[T], str: String): T = {
    assertParseableCompletely(p, str)
    assertParseable(p, str)
  }

  def assertParseableCompletely[T](p: P[_] => P[T], str: String): T = {
    def ended(pp: P[_]) = {
      implicit val ppp: P[_] = pp
      P(p(pp) ~ End)
    }
    assertParseable(ended, str)
  }

  def assertParseable[T](p: P[_] => P[T], str: String): T = {
    parse(str, p) match {
      case Parsed.Success(v, index) =>
        assert(index == str.length, s"Seems like value wasn't parsed completely: $v")
        v
      case Parsed.Failure(lp, idx, e) =>
        throw new IllegalStateException(s"Parsing failed: $lp, $idx, $e, ${e.trace()}, ${e.trace().msg}")
    }

  }


  def assertDomainParses(str: String): Unit = {
    val parsed = assertParseable(IDLParser.fullDomainDef(_), str)
    assert(parsed.model.definitions.nonEmpty)
    ()
  }
}
