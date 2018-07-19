package com.github.pshirshov.izumi.idealingua.il.parser.model

import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.IL

final case class ParsedDomain(did: IL.ILDomainId, imports: Seq[IL.ILDomainId], model: ParsedModel)


