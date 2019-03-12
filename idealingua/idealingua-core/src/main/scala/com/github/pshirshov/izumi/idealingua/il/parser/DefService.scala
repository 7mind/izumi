package com.github.pshirshov.izumi.idealingua.il.parser

import com.github.pshirshov.izumi.idealingua.il.parser.structure._
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.defns.RawMethod
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.defns.RawTypeDef.RawService
import fastparse.NoWhitespace._
import fastparse._


class DefService(context: IDLParserContext) {

  import context._
  import sep._

  def serviceBlock[_: P]: P[RawService] = P(metaAgg.cblock(kw.service, methods))
    .map {
      case (c, i, v) => RawService(i, v.toList, c)
    }

  // other method kinds should be added here
  protected[parser] def methods[_: P]: P[Seq[RawMethod]] = P(defSignature.method(kw.defm).rep(sep = any))
}
