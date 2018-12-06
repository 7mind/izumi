package com.github.pshirshov.izumi.idealingua.model.il.ast.raw

import com.github.pshirshov.izumi.idealingua.model.common.DomainId
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.DomainMetadata


final case class DomainDefinitionInterpreted(
                                              id: DomainId
                                              , meta: DomainMetadata
                                              , types: Seq[RawTypeDef]
                                              , services: Seq[raw.Service]
                                              , buzzers: Seq[raw.Buzzer]
                                              , streams: Seq[raw.Streams]
                                              , consts: Seq[raw.Constants]
                                              , imports: Seq[IL.ILImport]
                                              , referenced: Map[DomainId, DomainDefinitionInterpreted]
                                            )
