package com.github.pshirshov.izumi.idealingua.model.il.ast.raw

import com.github.pshirshov.izumi.idealingua.model.common.DomainId

final case class DomainDecl(id: DomainId, imports: Seq[Import], meta: RawNodeMeta)
