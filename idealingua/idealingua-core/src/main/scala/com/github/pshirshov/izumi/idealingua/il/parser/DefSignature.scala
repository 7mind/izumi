package com.github.pshirshov.izumi.idealingua.il.parser

import com.github.pshirshov.izumi.idealingua.il.parser.structure.{ids, sep}
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.RawMethod.Output
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.{RawMethod, RawNodeMeta, RawSimpleStructure}
import fastparse.all._

trait DefSignature {

  import sep._

  final val sigSep = P("=>" | "->" | ":" | "⇒")
  final val errSep = P("!!" | "?!" | "⥃" | "↬")

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

  final def void: Parser[Output.Void] = P( "(" ~ inline ~")" ).map(_ => RawMethod.Output.Void())
  final def adt: Parser[Output.Algebraic] = DefStructure.adtOut.map(v => RawMethod.Output.Algebraic(v.alternatives))
  final def struct: Parser[Output.Struct] = DefStructure.inlineStruct.map(v => RawMethod.Output.Struct(v))
  final def singular: Parser[Output.Singular] = ids.idGeneric.map(v => RawMethod.Output.Singular(v))

  final def output: Parser[Output.NonAlternativeOutput] = adt | struct | singular | void

  final def signature(keyword: Parser[Unit]): Parser[(RawNodeMeta, String, RawSimpleStructure, Option[(Output.NonAlternativeOutput, Option[Output.NonAlternativeOutput])])] = P(
    baseSignature(keyword) ~
      (any ~ sigSep ~ any ~ output ~ (any ~ errSep ~ any ~ output ).?).?
  )

  def toSignature(out: (RawNodeMeta, String, RawSimpleStructure, Option[(Output.NonAlternativeOutput, Option[Output.NonAlternativeOutput])])): RawMethod.RPCMethod = out match {
    case (c, id, in, None) =>
      RawMethod.RPCMethod(id, RawMethod.Signature(in, RawMethod.Output.Void()), c)

    case (c, id, in, Some((outGood, None))) =>
      RawMethod.RPCMethod(id, RawMethod.Signature(in, outGood), c)

    case (c, id, in, Some((outGood, Some(outBad)))) =>
      RawMethod.RPCMethod(id, RawMethod.Signature(in, RawMethod.Output.Alternative(outGood, outBad)), c)

    case f =>
      throw new IllegalStateException(s"Impossible case: $f")
  }
}

object DefSignature extends DefSignature {

}
