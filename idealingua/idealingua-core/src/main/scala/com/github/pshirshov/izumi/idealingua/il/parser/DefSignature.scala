package com.github.pshirshov.izumi.idealingua.il.parser

import com.github.pshirshov.izumi.idealingua.il.parser.structure.{aggregates, ids, sep}
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.defns.RawMethod.Output
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.defns.{RawAdt, RawMethod, RawNodeMeta, RawSimpleStructure}
import fastparse.NoWhitespace._
import fastparse._

class DefSignature(context: IDLParserContext) {

  import context._
  import sep._

  def sigSep[_: P]: P[Unit] = P("=>" | "->" | ":" | "⇒")

  def errSep[_: P]: P[Unit] = P("!!" | "?!" | "⥃" | "↬" | "or")


  def baseSignature[_: P](keyword: => P[Unit]): P[(String, RawSimpleStructure)] = P(
    keyword ~ inline ~
      ids.methodName ~ any ~
      defStructure.inlineStruct
  )

  def void[_: P]: P[Output.Void] = P("(" ~ inline ~ ")").map(_ => RawMethod.Output.Void())

  protected[parser] def adtOut[_: P]: P[RawAdt] = aggregates.enclosed(defStructure.adt(defStructure.sepAdtFreeForm))

  def adt[_: P]: P[Output.Algebraic] = (adtOut ~ defStructure.withAdtContract).map {
    case (v, contract) => RawMethod.Output.Algebraic(v.alternatives, contract)
  }

  def struct[_: P]: P[Output.Struct] = defStructure.inlineStruct.map(v => RawMethod.Output.Struct(v))

  def singular[_: P]: P[Output.Singular] = ids.typeReference.map(v => RawMethod.Output.Singular(v))

  def output[_: P]: P[Output.NonAlternativeOutput] = P(adt | struct | singular | void)

  def allOutputs[_: P]: P[Output] = P((any ~ sigSep ~ any ~ output ~ (any ~ errSep ~ any ~ output).?).?).map {
    case None =>
      RawMethod.Output.Void()
    case Some((outGood, None)) =>
      outGood
    case Some((outGood, Some(outBad))) =>
      RawMethod.Output.Alternative(outGood, outBad)
  }

  def signature[_: P](keyword: => P[Unit]): P[(RawNodeMeta, String, RawSimpleStructure, Output)] = P(
    metaAgg.withMeta(baseSignature(keyword) ~ allOutputs).map {
      case (meta, (id, input, output)) =>
        (meta, id, input, output)
    }
  )

  def method[_: P](keyword: => P[Unit]): P[RawMethod.RPCMethod] = P(defSignature.signature(keyword)).map {
    case (meta, id, in, out) =>
      RawMethod.RPCMethod(id, RawMethod.Signature(in, out), meta)
  }

}
