package com.github.pshirshov.izumi.idealingua.il.parser

import com.github.pshirshov.izumi.idealingua.il.parser.structure._
import com.github.pshirshov.izumi.idealingua.model.common.DomainId
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.domains
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.domains.{DomainHeader, Import}
import fastparse.NoWhitespace._
import fastparse._

class DefDomain(context: IDLParserContext)
  extends Identifiers
    with Aggregates {

  import context._

  def domainBlock[_: P]: P[DomainId] = P(kw.domain ~/ domainId)

  def importBlock[_: P]: P[Import] = metaAgg.withMeta(kw(kw.`import`, domainId ~ ("." ~ inline ~ enclosed(defStructure.imports(sep.sepStruct) ~ sepStruct.?)).?))
    .map {
      case (meta, (id, names)) =>
        names match {
          case Some(nn) =>
            domains.Import(id, nn, meta)
          case None =>
            Import(id, Seq.empty, meta)
        }
    }

  def decl[_: P]: P[DomainHeader] = P(metaAgg.withMeta(domainBlock ~ any ~ importBlock.rep(sep = any))).map {
    case (meta, (id, imports)) =>
      DomainHeader(id, imports, meta)
  }
}


