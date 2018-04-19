package com.github.pshirshov.izumi.idealingua.model.il.ast.typed

import com.github.pshirshov.izumi.idealingua.model.common.DomainId

case class DomainDefinition(
                             id: DomainId
                             , types: Seq[TypeDef]
                             , services: Seq[Service]
                             , referenced: Map[DomainId, DomainDefinition]
                           )
