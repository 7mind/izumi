package com.github.pshirshov.izumi.idealingua.model.il.ast.raw

import com.github.pshirshov.izumi.idealingua.model.common.DomainId
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw

final case class DomainDefinitionParsed(
                                         id: DomainId
                                         , types: Seq[IL.Val]
                                         , referenced: Map[DomainId, DomainDefinitionParsed]
                                       )


final case class DomainDefinitionInterpreted(
                                              id: DomainId
                                              , types: Seq[RawTypeDef]
                                              , services: Seq[raw.Service]
                                              , imports: Seq[IL.ILImport]
                                              , referenced: Map[DomainId, DomainDefinitionInterpreted]
                                            )
