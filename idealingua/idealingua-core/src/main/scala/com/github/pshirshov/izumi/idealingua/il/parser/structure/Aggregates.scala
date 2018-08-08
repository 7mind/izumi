package com.github.pshirshov.izumi.idealingua.il.parser.structure

import com.github.pshirshov.izumi.idealingua.il.parser.DefSignature
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.{ParsedId, RawNodeMeta}
import fastparse.all._

trait Aggregates
  extends Separators
    with Identifiers {



  def enclosed[T](defparser: Parser[T]): Parser[T] = {
    P(("{" ~ any ~ defparser ~ any ~ "}") | "(" ~ any ~ defparser ~ any ~ ")")
  }

  def enclosedB[T](defparser: Parser[T]): Parser[T] = {
    P("[" ~ any ~ defparser ~ any ~ "]")
  }


  def starting[T](keyword: Parser[Unit], defparser: Parser[T]): Parser[(ParsedId, T)] = {
    kw(keyword, idShort ~ inline ~ defparser)
  }

  def block[T](keyword: Parser[Unit], defparser: Parser[T]): Parser[(ParsedId, T)] = {
    starting(keyword, enclosed(defparser))
  }

  def cstarting[T](keyword: Parser[Unit], defparser: Parser[T]): Parser[(RawNodeMeta, ParsedId, T)] = {
    (DefSignature.meta ~ starting(keyword, defparser)).map {
      case (m, (i, t)) => (m, i, t)
    }
  }

  def cblock[T](keyword: Parser[Unit], defparser: Parser[T]): Parser[(RawNodeMeta, ParsedId, T)] = {
    (DefSignature.meta ~ block(keyword, defparser)).map {
      case (m, (i, t)) => (m, i, t)
    }
  }

}

