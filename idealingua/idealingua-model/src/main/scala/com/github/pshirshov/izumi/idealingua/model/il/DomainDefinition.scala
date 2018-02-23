package com.github.pshirshov.izumi.idealingua.model.il

case class DomainDefinition(
                             id: DomainId
                             , types: Seq[FinalDefinition]
                             , services: Seq[Service]
                             , referenced: Map[DomainId, DomainDefinition]
                           )
