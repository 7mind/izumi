package com.github.pshirshov.izumi.idealingua.il.parser

import com.github.pshirshov.izumi.idealingua.il.parser.model.AlgebraicType
import com.github.pshirshov.izumi.idealingua.il.parser.structure._
import com.github.pshirshov.izumi.idealingua.model.common.AbstractIndefiniteId
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.IL.ILEmitter
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw._
import fastparse.all._


trait DefEmitter {

  import sep._

  final val method = DefSignature.signature(kw.defe).map {
    case (c, id, in, Some(out: RawSimpleStructure)) =>
      RawMethod.RPCMethod(id, RawMethod.Signature(in, RawMethod.Output.Struct(out)), c)

    case (c, id, in, Some(out: AlgebraicType)) =>
      RawMethod.RPCMethod(id, RawMethod.Signature(in, RawMethod.Output.Algebraic(out.alternatives)), c)

    case (c, id, in, Some(out: AbstractIndefiniteId)) =>
      RawMethod.RPCMethod(id, RawMethod.Signature(in, RawMethod.Output.Singular(out)), c)

    case (c, id, in, None) =>
      RawMethod.RPCMethod(id, RawMethod.Signature(in, RawMethod.Output.Void()), c)

    case f =>
      throw new IllegalStateException(s"Impossible case: $f")
  }

  // other method kinds should be added here
  final val methods: Parser[Seq[RawMethod]] = P(method.rep(sep = any))

  final val emitterBlock = aggregates.cblock(kw.emitter, methods)
    .map {
      case (c, i, v) => ILEmitter(Emitter(i.toEmitterId, v.toList, c))
    }

}

object DefEmitter extends DefEmitter {
}




