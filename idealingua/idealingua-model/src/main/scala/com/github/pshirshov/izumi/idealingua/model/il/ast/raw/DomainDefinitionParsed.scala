package com.github.pshirshov.izumi.idealingua.model.il.ast.raw

import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.DomainId

case class DomainDefinitionParsed(
                             id: DomainId
                             , types: Seq[ILAstParsed]
                             , services: Seq[ILAstParsed.Service]
                             , referenced: Map[DomainId, DomainDefinitionParsed]
                           )
