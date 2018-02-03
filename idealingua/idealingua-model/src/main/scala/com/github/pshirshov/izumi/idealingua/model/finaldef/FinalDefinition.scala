package com.github.pshirshov.izumi.idealingua.model.finaldef

import com.github.pshirshov.izumi.idealingua.model.common.TypeId.{AliasId, DTOId, IdentifierId, InterfaceId}
import com.github.pshirshov.izumi.idealingua.model.common._

sealed trait FinalDefinition {
  def id: TypeId
}


object FinalDefinition {

  case class Alias(id: AliasId, target: TypeId) extends FinalDefinition

  case class Identifier(id: IdentifierId, fields: Seq[Field]) extends FinalDefinition

  case class Interface(id: InterfaceId, ownFields: Seq[Field], interfaces: Seq[TypeId]) extends FinalDefinition

  case class DTO(id: DTOId, interfaces: Seq[TypeId]) extends FinalDefinition
}







