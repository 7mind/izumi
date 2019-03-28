package com.github.pshirshov.izumi.idealingua.model.il.ast.raw.domains

import com.github.pshirshov.izumi.idealingua.model.common.DomainId
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.defns.RawNodeMeta

final case class Import(id: DomainId, identifiers: Seq[ImportedId], meta: RawNodeMeta)
