package com.github.pshirshov.izumi.idealingua.il

import com.github.pshirshov.izumi.idealingua.il.IL.{ILDef, ILInclude, ILService}
import com.github.pshirshov.izumi.idealingua.model.il.DomainDefinition

case class ParsedDomain(domain: DomainDefinition, includes: Seq[String], imports: Seq[String]) {
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
  def apply(did: IL.ILDomainId, imports: Seq[String], defs: Seq[IL.Val]): ParsedDomain = {
    val includes = defs.collect({ case d: ILInclude => d.i })

    ParsedDomain(DomainDefinition(did.v, Seq.empty, Seq.empty), includes, imports).extend(defs)
  }
}
