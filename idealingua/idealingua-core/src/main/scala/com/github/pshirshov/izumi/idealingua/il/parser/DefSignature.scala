package com.github.pshirshov.izumi.idealingua.il.parser

import com.github.pshirshov.izumi.idealingua.il.parser.structure.{ids, sep}
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.RawMethod.Output
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.{RawMethod, RawNodeMeta, RawSimpleStructure}
import fastparse._
import fastparse.NoWhitespace._

trait DefSignature {

  import sep._

  def sigSep[_:P]: P[Unit] = P("=>" | "->" | ":" | "⇒")
  def errSep[_:P]: P[Unit] = P("!!" | "?!" | "⥃" | "↬")

  def meta[_:P]: P[RawNodeMeta] = P(MaybeDoc ~ DefConst.defAnnos)
    .map {
      case (d, a) => RawNodeMeta(d, a)
    }

  def baseSignature[_:P](keyword: => P[Unit]): P[(RawNodeMeta, String, RawSimpleStructure)] = P(
    meta ~
      keyword ~ inline ~
      ids.symbol ~ any ~
      DefStructure.inlineStruct
  )

  def void[_:P]: P[Output.Void] = P( "(" ~ inline ~")" ).map(_ => RawMethod.Output.Void())
  def adt[_:P]: P[Output.Algebraic] = DefStructure.adtOut.map(v => RawMethod.Output.Algebraic(v.alternatives))
  def struct[_:P]: P[Output.Struct] = DefStructure.inlineStruct.map(v => RawMethod.Output.Struct(v))
  def singular[_:P]: P[Output.Singular] = ids.idGeneric.map(v => RawMethod.Output.Singular(v))

  def output[_:P]: P[Output.NonAlternativeOutput] = P(adt | struct | singular | void)

  def signature[_:P](keyword: => P[Unit]): P[(RawNodeMeta, String, RawSimpleStructure, Option[(Output.NonAlternativeOutput, Option[Output.NonAlternativeOutput])])] = P(
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
