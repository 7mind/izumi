package izumi.idealingua.il.parser

import izumi.idealingua.model.loader.FSPath
import fastparse.NoWhitespace._
import fastparse._

trait ParserTestTools {
  val ctx = IDLParserContext(FSPath.Name("ParserTestTools.domain"))

  def assertParsesInto[T](p: P[_] => P[T], src: String, expected: T): Unit = {
    assert(assertParses(p, src) == expected)
  }

  def assertParses[T](p: P[_] => P[T], str: String): T = {
    assertParseableCompletely(p, str)
    assertParseable(p, str)
  }

  protected def ended[T](pp: P[_], p: P[_] => P[T]): P[T] = {
    implicit val ppp: P[_] = pp
    P(p(pp) ~ End)
  }

  def assertParseableCompletely[T](p: P[_] => P[T], str: String): T = {
    assertParseable(ended(_, p), str)
  }

  def assertParseable[T](p: P[_] => P[T], str: String): T = {
    parse(str, p) match {
      case Parsed.Success(v, index) =>
        assert(index == str.length, s"Seems like value wasn't parsed completely: $v")
        v
      case Parsed.Failure(lp, idx, e) =>
        throw new IllegalStateException(
          s"""Parsing failed at ${e.startIndex}..$idx:
             |  label  : $lp
             |  message: ${e.trace().msg}
             |  trace  : ${e.trace()}
             |  stack  : ${e.stack}
             |  input  : ${e.input}
             |  parser : ${e.originalParser}
             |  """.stripMargin)
    }

  }


  def assertDomainParses(str: String): Unit = {
    val parsed = assertParseable(ctx.defParsers.fullDomainDef(_), str)
    assert(parsed.model.definitions.nonEmpty)
    ()
  }
}
