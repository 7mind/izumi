package com.github.pshirshov.izumi.idealingua.model.il.ast.raw

import com.github.pshirshov.izumi.idealingua.model.common.DomainId
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw
import com.github.pshirshov.izumi.idealingua.model.loader.FSPath


final case class DomainDefinitionInterpreted(
                                              id: DomainId,
                                              origin: FSPath,
                                              directInclusions: Seq[String],
                                              meta: RawNodeMeta,
                                              types: Seq[RawTypeDef],
                                              services: Seq[raw.Service],
                                              buzzers: Seq[raw.Buzzer],
                                              streams: Seq[raw.Streams],
                                              consts: Seq[raw.Constants],
                                              imports: Seq[SingleImport],
                                              referenced: Map[DomainId, DomainDefinitionInterpreted],
                                            )
