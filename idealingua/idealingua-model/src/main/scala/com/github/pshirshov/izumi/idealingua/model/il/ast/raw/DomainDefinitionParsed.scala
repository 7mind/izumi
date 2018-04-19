package com.github.pshirshov.izumi.idealingua.model.il.ast.raw

import com.github.pshirshov.izumi.idealingua.model.common.DomainId
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw

case class DomainDefinitionParsed(
                             id: DomainId
                             , types: Seq[RawTypeDef]
                             , services: Seq[raw.Service]
                             , referenced: Map[DomainId, DomainDefinitionParsed]
                           )
