package com.github.pshirshov.izumi.idealingua.model.il

case class DomainDefinitionParsed(
                             id: DomainId
                             , types: Seq[ILAstParsed]
                             , services: Seq[ILAstParsed.Service]
                             , referenced: Map[DomainId, DomainDefinitionParsed]
                           )
