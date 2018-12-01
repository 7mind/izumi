package com.github.pshirshov.izumi.idealingua.il.parser.structure

import com.github.pshirshov.izumi.idealingua.model.common.{AbstractIndefiniteId, DomainId}
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.ParsedId
import fastparse.CharPredicates.{isDigit, isLetter}
import fastparse._, NoWhitespace._

trait Identifiers extends Separators {
  final def symbol[_:P]: P[String] = P(CharPred(c => isLetter(c)) ~ CharPred(c => isLetter(c) | isDigit(c) | c == '_').rep).!

  final def idPkg[_:P]: P[Seq[String]] = P(symbol.rep(sep = "."))
  final def domainId[_:P]: P[DomainId] = P(idPkg)
    .map(v => DomainId(v.init, v.last))

  final def idFull[_:P]: P[ParsedId] = P(idPkg ~ "#" ~/ symbol).map(v => ParsedId(v._1, v._2))
  final def idShort[_:P]: P[ParsedId] = P(symbol).map(v => ParsedId(v))
  final def identifier[_:P]: P[ParsedId] = P(idFull | idShort)

  final def idGeneric[_:P]: P[AbstractIndefiniteId] = P(inline ~ identifier ~ inline ~ generic.rep(min = 0, max = 1) ~ inline)
    .map(tp => tp._1.toGeneric(tp._2))

  final def generic[_:P]: P[Seq[AbstractIndefiniteId]] = P("[" ~/ inline ~ idGeneric.rep(sep = ",") ~ inline ~ "]")

}

