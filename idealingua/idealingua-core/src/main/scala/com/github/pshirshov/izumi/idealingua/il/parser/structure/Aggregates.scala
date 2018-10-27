package com.github.pshirshov.izumi.idealingua.il.parser.structure

import com.github.pshirshov.izumi.idealingua.il.parser.DefSignature
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.{ParsedId, RawNodeMeta}
import fastparse._

trait Aggregates
  extends Separators
    with Identifiers {



  def enclosed[T](defparser: P[T]): P[T] = {
    P(("{" ~ any ~ defparser ~ any ~ "}") | "(" ~ any ~ defparser ~ any ~ ")")
  }

  def enclosedB[T](defparser: P[T]): P[T] = {
    P("[" ~ any ~ defparser ~ any ~ "]")
  }


  def starting[T](keyword: P[Unit], defparser: P[T]): P[(ParsedId, T)] = {
    kw(keyword, idShort ~ inline ~ defparser)
  }

  def block[T](keyword: P[Unit], defparser: P[T]): P[(ParsedId, T)] = {
    starting(keyword, enclosed(defparser))
  }

  def cstarting[T](keyword: P[Unit], defparser: P[T]): P[(RawNodeMeta, ParsedId, T)] = {
    (DefSignature.meta ~ starting(keyword, defparser)).map {
      case (m, (i, t)) => (m, i, t)
    }
  }

  def cblock[T](keyword: P[Unit], defparser: P[T]): P[(RawNodeMeta, ParsedId, T)] = {
    (DefSignature.meta ~ block(keyword, defparser)).map {
      case (m, (i, t)) => (m, i, t)
    }
  }

}

