package com.github.pshirshov.izumi.idealingua.il

import com.github.pshirshov.izumi.idealingua.il.IL.{ILDef, ILService}
import com.github.pshirshov.izumi.idealingua.model.il.{DomainDefinitionParsed, DomainId}

case class ParsedDomain(domain: DomainDefinitionParsed, imports: Seq[DomainId], includes: Seq[String]) {
  def extend(model: LoadedModel): ParsedDomain = {
    val types = model.definitions.collect({ case d: ILDef => d.v })
    val services = model.definitions.collect({ case d: ILService => d.v })

    val extendedDomain = domain.copy(
      types = domain.types ++ types
      , services = domain.services ++ services
    )

    this.copy(domain = extendedDomain)
  }
}

object ParsedDomain {
  def apply(did: IL.ILDomainId, imports: Seq[IL.ILDomainId], defs: ParsedModel): ParsedDomain = {
    val domain = DomainDefinitionParsed(did.id, Seq.empty, Seq.empty, Map.empty)

    val parsedDomain = ParsedDomain(
      domain
      , imports.map(_.id)
      , defs.includes
    )

    parsedDomain.extend(LoadedModel(defs.definitions))
  }
}
