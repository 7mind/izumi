package com.github.pshirshov.izumi.idealingua.il.parser

import com.github.pshirshov.izumi.idealingua.il.parser.structure._
import com.github.pshirshov.izumi.idealingua.model.common.DomainId
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.IL.Import
import fastparse._
import fastparse.NoWhitespace._

trait DefDomain
  extends Identifiers
    with Aggregates {
  def domainBlock[_:P]: P[DomainId] = P(kw.domain ~/ domainId)

  def importBlock[_:P]: P[Import] = kw(kw.`import`, domainId ~ ("." ~ inline ~ enclosed(DefStructure.imports(sep.sepStruct))).?).map {
    case (id, names) =>
      names match {
        case Some(nn) =>
          Import(id, nn.toSet)
        case None =>
          Import(id, Set.empty)
      }
  }

  def decl[_:P]: P[(DomainId, Seq[Import])] = P(domainBlock ~ any ~ importBlock.rep(sep = any))
}


object DefDomain extends DefDomain {

}
