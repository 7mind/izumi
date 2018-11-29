package com.github.pshirshov.izumi.idealingua.model.il.ast.raw

import com.github.pshirshov.izumi.idealingua.model.common.DomainId
import com.github.pshirshov.izumi.idealingua.model.loader.FSPath

final case class DomainDefinitionParsed(
                                         id: DomainId,
                                         members: Seq[IL.Val],
                                         referenced: Map[DomainId, DomainDefinitionParsed],
                                         origin: FSPath,
                                       )
