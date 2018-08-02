package com.github.pshirshov.izumi.idealingua.model.il.ast.typed

import com.github.pshirshov.izumi.idealingua.model.common.DomainId

final case class DomainDefinition(
                             id: DomainId
                             , types: Seq[TypeDef]
                             , services: Seq[Service]
                             , emitters: Seq[Emitter]
                             , streams: Seq[Streams]
                             , referenced: Map[DomainId, DomainDefinition]
                           ) {
  def nonEmpty: Boolean = types.nonEmpty || services.nonEmpty || emitters.nonEmpty || streams.nonEmpty
}
