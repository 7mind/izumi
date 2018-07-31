package com.github.pshirshov.izumi.idealingua.il.parser

import com.github.pshirshov.izumi.idealingua.il.parser.structure._
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.IL.Import
import fastparse.all._

trait DefDomain
  extends Identifiers
    with Aggregates {
  final val domainBlock = P(kw.domain ~/ domainId)

  final val importBlock = kw(kw.`import`, domainId ~ ("." ~ inline ~ enclosed(DefStructure.imports(sep.sepStruct))).?).map {
    case (id, names) =>
      names match {
        case Some(nn) =>
          Import(id, nn.toSet)
        case None =>
          Import(id, Set.empty)
      }
  }

  final val decl = P(domainBlock ~ any ~ importBlock.rep(sep = any))
}


object DefDomain extends DefDomain {

}
