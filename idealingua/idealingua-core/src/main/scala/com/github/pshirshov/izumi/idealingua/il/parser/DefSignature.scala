package com.github.pshirshov.izumi.idealingua.il.parser

import com.github.pshirshov.izumi.idealingua.il.parser.structure.{ids, sep}
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.{RawNodeMeta, RawSimpleStructure}
import fastparse.all._

trait DefSignature {

  import sep._

  final val sigSep = P("=>" | "->" | ":")

  final val meta = (MaybeDoc ~ DefConst.defAnnos)
    .map {
      case (d, a) => RawNodeMeta(d, a)
    }

  final def baseSignature(keyword: Parser[Unit]): Parser[(RawNodeMeta, String, RawSimpleStructure)] = P(
    meta ~
      keyword ~ inline ~
      ids.symbol ~ any ~
      DefStructure.inlineStruct
  )

  final def signature(keyword: Parser[Unit]): Parser[(RawNodeMeta, String, RawSimpleStructure, Option[Object])] = P(
    baseSignature(keyword) ~
      (any ~ sigSep ~ any ~ (DefStructure.adtOut | DefStructure.inlineStruct | ids.idGeneric)).?
  )
}

object DefSignature extends DefSignature {

}
