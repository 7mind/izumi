package com.github.pshirshov.izumi.idealingua.model.il.ast.raw

import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.DomainId
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw

case class DomainDefinitionParsed(
                             id: DomainId
                             , types: Seq[ILAstParsed]
                             , services: Seq[raw.Service]
                             , referenced: Map[DomainId, DomainDefinitionParsed]
                           )
