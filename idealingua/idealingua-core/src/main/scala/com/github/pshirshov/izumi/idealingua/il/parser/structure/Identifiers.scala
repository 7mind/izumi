package com.github.pshirshov.izumi.idealingua.il.parser.structure

import com.github.pshirshov.izumi.idealingua.model.common.{AbstractIndefiniteId, DomainId}
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.ParsedId
import fastparse.CharPredicates.{isDigit, isLetter}
import fastparse.all._

trait Identifiers extends Separators {
  final val symbol = P(CharPred(c => isLetter(c)) ~ CharPred(c => isLetter(c) | isDigit(c) | c == '_').rep).!

  final val idPkg = P(symbol.rep(sep = "."))
  final val domainId = P(idPkg)
    .map(v => DomainId(v.init, v.last))

  final val idFull = P(idPkg ~ "#" ~/ symbol).map(v => ParsedId(v._1, v._2))
  final val idShort = P(symbol).map(v => ParsedId(v))
  final val identifier = P(idFull | idShort)

  final lazy val idGeneric: Parser[AbstractIndefiniteId] = P(inline ~ identifier ~ inline ~ generic.rep(min = 0, max = 1) ~ inline)
    .map(tp => tp._1.toGeneric(tp._2))

  final lazy val generic = P("[" ~/ inline ~ idGeneric.rep(sep = ",") ~ inline ~ "]")

}

