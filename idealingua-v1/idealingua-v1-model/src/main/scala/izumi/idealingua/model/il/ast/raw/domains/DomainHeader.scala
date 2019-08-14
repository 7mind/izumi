package com.github.pshirshov.izumi.idealingua.model.il.ast.raw.domains

import com.github.pshirshov.izumi.idealingua.model.common.DomainId
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.defns.RawNodeMeta

final case class DomainHeader(id: DomainId, imports: Seq[Import], meta: RawNodeMeta)
