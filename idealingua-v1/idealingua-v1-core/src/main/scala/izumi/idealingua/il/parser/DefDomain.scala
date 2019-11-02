package izumi.idealingua.il.parser

import izumi.idealingua.il.parser.structure._
import izumi.idealingua.model.common.DomainId
import izumi.idealingua.model.il.ast.raw.domains
import izumi.idealingua.model.il.ast.raw.domains.{DomainHeader, Import}
import fastparse.NoWhitespace._
import fastparse._

class DefDomain(context: IDLParserContext)
  extends Identifiers
    with Aggregates {

  import context._

  def domainBlock[_: P]: P[DomainId] = P(kw.domain ~/ domainId)

  def importBlock[_: P]: P[Import] = kw(kw.`import`, domainId ~ ("." ~ inline ~ enclosed(defStructure.imports(sep.sepStruct) ~ sepStruct.?)).?)
    .map {
      case (id, names) =>
        names match {
          case Some(nn) =>
            domains.Import(id, nn.toSet)
          case None =>
            Import(id, Set.empty)
        }
    }

  def decl[_: P]: P[DomainHeader] = P(metaAgg.withMeta(domainBlock ~ any ~ importBlock.rep(sep = any))).map {
    case (meta, (id, imports)) =>
      DomainHeader(id, imports, meta)
  }
}


