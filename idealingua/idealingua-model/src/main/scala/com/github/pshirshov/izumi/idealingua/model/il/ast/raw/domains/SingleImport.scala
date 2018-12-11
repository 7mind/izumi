package com.github.pshirshov.izumi.idealingua.model.il.ast.raw.domains

import com.github.pshirshov.izumi.idealingua.model.common.DomainId

final case class SingleImport(domain: DomainId, imported: ImportedId)
