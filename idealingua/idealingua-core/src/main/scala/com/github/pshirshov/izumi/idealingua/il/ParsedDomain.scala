package com.github.pshirshov.izumi.idealingua.il

import com.github.pshirshov.izumi.idealingua.il.IL.{ILDef, ILInclude, ILService}
import com.github.pshirshov.izumi.idealingua.model.il.{DomainDefinitionParsed, DomainId}

case class ParsedDomain(domain: DomainDefinitionParsed, includes: Seq[String], imports: Seq[DomainId]) {
  def extend(defs: Seq[IL.Val]): ParsedDomain = {
    val types = defs.collect({ case d: ILDef => d.v })
    val services = defs.collect({ case d: ILService => d.v })

    val extendedDomain = domain.copy(
      types = domain.types ++ types
      , services = domain.services ++ services
    )

    this.copy(domain = extendedDomain)
  }
}

object ParsedDomain {
  def apply(did: IL.ILDomainId, imports: Seq[IL.ILDomainId], defs: Seq[IL.Val]): ParsedDomain = {
    val includes = defs.collect({ case d: ILInclude => d.i })

    ParsedDomain(
      DomainDefinitionParsed(did.id, Seq.empty, Seq.empty, Map.empty)
      , includes
      , imports.map(_.id)
    ).extend(defs)
  }
}
