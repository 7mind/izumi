package com.github.pshirshov.izumi.idealingua.model.il.ast

case class DomainDefinition(
                             id: DomainId
                             , types: Seq[ILAst]
                             , services: Seq[ILAst.Service]
                             , referenced: Map[DomainId, DomainDefinition]
                           )


