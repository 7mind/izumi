package com.github.pshirshov.izumi.idealingua.il.parser

import com.github.pshirshov.izumi.idealingua.il.parser.structure._
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.IL.ILService
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.{RawMethod, Service}
import fastparse._, NoWhitespace._


class DefService(context: IDLParserContext) {
  import context._
  import sep._

  def method[_:P]: P[RawMethod.RPCMethod] = defSignature.signature(kw.defm).map(defSignature.toSignature)

  // other method kinds should be added here
  def methods[_:P]: P[Seq[RawMethod]] = P(method.rep(sep = any))

  def serviceBlock[_:P]: P[ILService] = aggregates.cblock(kw.service, methods)
    .map {
      case (c, i, v) => ILService(Service(i.toServiceId, v.toList, c))
    }

}
