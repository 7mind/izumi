package com.github.pshirshov.izumi.idealingua.il


case class ParsedDomain(did: IL.ILDomainId, imports: Seq[IL.ILDomainId], model: ParsedModel)


