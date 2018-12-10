package com.github.pshirshov.izumi.idealingua.il.parser

import com.github.pshirshov.izumi.idealingua.il.parser.structure._
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.{RawMethod, Service, TopLevelDefn}
import fastparse.NoWhitespace._
import fastparse._


class DefService(context: IDLParserContext) {

  import context._
  import sep._

  // other method kinds should be added here
  def methods[_: P]: P[Seq[RawMethod]] = P(defSignature.method(kw.defm).rep(sep = any))

  def serviceBlock[_: P]: P[TopLevelDefn.TLDService] = P(metaAgg.cblock(kw.service, methods))
    .map {
      case (c, i, v) => Service(i.toServiceId, v.toList, c)
    }
    .map(TopLevelDefn.TLDService)

}
