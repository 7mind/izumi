package com.github.pshirshov.izumi.idealingua.model.il.parsing

import com.github.pshirshov.izumi.idealingua.model.il.ast.DomainId

case class DomainDefinitionParsed(
                             id: DomainId
                             , types: Seq[ILAstParsed]
                             , services: Seq[ILAstParsed.Service]
                             , referenced: Map[DomainId, DomainDefinitionParsed]
                           )
