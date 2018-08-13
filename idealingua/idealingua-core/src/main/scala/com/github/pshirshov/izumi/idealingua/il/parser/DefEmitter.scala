package com.github.pshirshov.izumi.idealingua.il.parser

import com.github.pshirshov.izumi.idealingua.il.parser.structure._
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.IL.ILEmitter
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw._
import fastparse.all._


trait DefEmitter {

  import sep._

  final val method = DefSignature.signature(kw.defe).map(DefSignature.toSignature)

  // other method kinds should be added here
  final val methods: Parser[Seq[RawMethod]] = P(method.rep(sep = any))

  final val emitterBlock = aggregates.cblock(kw.emitter, methods)
    .map {
      case (c, i, v) => ILEmitter(Emitter(i.toEmitterId, v.toList, c))
    }

}

object DefEmitter extends DefEmitter {
}




