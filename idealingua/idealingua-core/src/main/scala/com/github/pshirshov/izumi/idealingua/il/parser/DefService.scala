package com.github.pshirshov.izumi.idealingua.il.parser

import com.github.pshirshov.izumi.idealingua.il.parser.structure._
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.IL.ILService
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.{RawMethod, Service}
import fastparse._, NoWhitespace._


trait DefService {

  import sep._

  final def method[_:P]: P[RawMethod.RPCMethod] = DefSignature.signature(kw.defm).map(DefSignature.toSignature)

  // other method kinds should be added here
  final def methods[_:P]: P[Seq[RawMethod]] = P(method.rep(sep = any))

  final def serviceBlock[_:P]: P[ILService] = aggregates.cblock(kw.service, methods)
    .map {
      case (c, i, v) => ILService(Service(i.toServiceId, v.toList, c))
    }

}

object DefService extends DefService {
}
