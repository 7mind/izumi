package com.github.pshirshov.izumi.idealingua.il.parser.structure

import com.github.pshirshov.izumi.idealingua.il.parser.DefConst
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.{ParsedId, RawNodeMeta}
import fastparse.NoWhitespace._
import fastparse._

trait Aggregates
  extends Separators
    with Identifiers {



  def enclosed[T](defparser: => P[T])(implicit v: P[_]): P[T] = {
    P(("{" ~ any ~ defparser ~ any ~ "}") | "(" ~ any ~ defparser ~ any ~ ")")
  }

  def enclosedB[T](defparser: => P[T])(implicit v: P[_]): P[T] = {
    P("[" ~ any ~ defparser ~ any ~ "]")
  }


  def starting[T](keyword: => P[Unit], defparser: => P[T])(implicit v: P[_]): P[(ParsedId, T)] = {
    kw(keyword, idShort ~ inline ~ defparser)
  }

  def block[T](keyword: => P[Unit], defparser: => P[T])(implicit v: P[_]): P[(ParsedId, T)] = {
    starting(keyword, enclosed(defparser))
  }

  def meta[_:P]: P[RawNodeMeta] = P(MaybeDoc ~ DefConst.defAnnos)
    .map {
      case (d, a) => RawNodeMeta(d, a)
    }


  def cstarting[T](keyword: => P[Unit], defparser: => P[T])(implicit v: P[_]): P[(RawNodeMeta, ParsedId, T)] = {
    (meta ~ starting(keyword, defparser)).map {
      case (m, (i, t)) => (m, i, t)
    }
  }

  def cblock[T](keyword: => P[Unit], defparser: => P[T])(implicit v: P[_]): P[(RawNodeMeta, ParsedId, T)] = {
    (meta ~ block(keyword, defparser)).map {
      case (m, (i, t)) => (m, i, t)
    }
  }

}

