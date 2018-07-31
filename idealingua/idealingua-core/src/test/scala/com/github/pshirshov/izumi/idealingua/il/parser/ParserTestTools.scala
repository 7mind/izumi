package com.github.pshirshov.izumi.idealingua.il.parser

import fastparse.all._
import fastparse.core.Parsed

trait ParserTestTools {
  def assertParsesInto[T](p: Parser[T], src: String, expected: T): Unit = {
    assert(assertParses(p, src) == expected)
  }

  def assertParses[T](p: Parser[T], str: String): T = {
    assertParseableCompletely(p, str)
    assertParseable(p, str)
  }

  def assertParseableCompletely[T](p: Parser[T], str: String): T = {
    assertParseable(p ~ End, str)
  }

  def assertParseable[T](p: Parser[T], str: String): T = {
    p.parse(str) match {
      case Parsed.Success(v, index) =>
        assert(index == str.length, s"Seems like value wasn't parsed completely: $v")
        v
      case Parsed.Failure(lp, idx, e) =>
        throw new IllegalStateException(s"Parsing failed: $lp, $idx, $e, ${e.traced}, ${e.traced.trace}")
    }

  }


  def assertDomainParses(str: String): Unit = {
    val parsed = assertParseable(IDLParser.fullDomainDef, str)
    assert(parsed.model.definitions.nonEmpty)
    ()
  }
}
