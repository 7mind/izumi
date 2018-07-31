package com.github.pshirshov.izumi.idealingua.il.parser

import com.github.pshirshov.izumi.idealingua.il.parser.model.AlgebraicType
import com.github.pshirshov.izumi.idealingua.il.parser.structure._
import com.github.pshirshov.izumi.idealingua.model.common.AbstractIndefiniteId
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.RawSimpleStructure
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.Service.DefMethod
import fastparse.all._

trait DefService {
  import sep._
  final val sigSep = P("=>" | "->" | ":")
  final val inlineStruct = aggregates.enclosed(DefStructure.simpleStruct)
  final val adtOut = aggregates.enclosed(DefStructure.adt(sepAdt))

  final val defMethod = P(
    kw.defm ~ inline ~
      ids.symbol ~ any ~
      inlineStruct ~ any ~
      sigSep ~ any ~
      (adtOut | inlineStruct | ids.idGeneric)
  ).map {
    case (id, in, out: RawSimpleStructure) =>
      DefMethod.RPCMethod(id, DefMethod.Signature(in, DefMethod.Output.Struct(out)))

    case (id, in, out: AlgebraicType) =>
      DefMethod.RPCMethod(id, DefMethod.Signature(in, DefMethod.Output.Algebraic(out.alternatives)))

    case (id, in, out: AbstractIndefiniteId) =>
      DefMethod.RPCMethod(id, DefMethod.Signature(in, DefMethod.Output.Singular(out)))

    case f =>
      throw new IllegalStateException(s"Impossible case: $f")
  }


  final val sigParam = P(inline ~ ids.identifier ~ inline)
  final val signature = P(sigParam.rep(sep = ","))

  // other method kinds should be added here
  final val method: Parser[DefMethod] = P(defMethod)
  final val methods: Parser[Seq[DefMethod]] = P(method.rep(sep = any))

}

object DefService extends DefService {
}
