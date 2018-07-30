package com.github.pshirshov.izumi.idealingua.il.parser.model

import com.github.pshirshov.izumi.idealingua.model.common.DomainId
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.IL

final case class ParsedDomain(
                               did: DomainId
                               , imports: Seq[IL.Import]
                               , model: ParsedModel
                             )


