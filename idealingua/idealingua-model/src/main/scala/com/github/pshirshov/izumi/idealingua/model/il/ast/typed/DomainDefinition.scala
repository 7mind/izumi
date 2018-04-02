package com.github.pshirshov.izumi.idealingua.model.il.ast.typed

case class DomainDefinition(
                             id: DomainId
                             , types: Seq[TypeDef]
                             , services: Seq[Service]
                             , referenced: Map[DomainId, DomainDefinition]
                           )
