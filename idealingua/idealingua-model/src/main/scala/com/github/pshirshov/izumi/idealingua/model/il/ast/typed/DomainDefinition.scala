package com.github.pshirshov.izumi.idealingua.model.il.ast.typed

import com.github.pshirshov.izumi.idealingua.model.common.DomainId
import com.github.pshirshov.izumi.idealingua.model.loader.FSPath

case class Inclusion(include: String)


final case class DomainMetadata(origin: FSPath, directInclusions: Seq[Inclusion], meta: NodeMeta)


final case class DomainDefinition(
                                   id: DomainId
                                   , meta: DomainMetadata
                                   , types: Seq[TypeDef]
                                   , services: Seq[Service]
                                   , buzzers: Seq[Buzzer]
                                   , streams: Seq[Streams]
                                   , referenced: Map[DomainId, DomainDefinition] // there may be a referential loop
                                 ) {
  def nonEmpty: Boolean = types.nonEmpty || services.nonEmpty || buzzers.nonEmpty || streams.nonEmpty

  override def hashCode(): Int = id.hashCode()

  override def equals(obj: Any): Boolean = {
    obj match {
      case d: DomainDefinition =>
        id == d.id
      case _ =>
        false
    }
  }
}
