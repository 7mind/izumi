package com.github.pshirshov.izumi.idealingua.model.finaldef

import com.github.pshirshov.izumi.idealingua.model.{Field, TypeId}

sealed trait FinalDefinition {
  def id: TypeId
}


object FinalDefinition {

  case class Alias(id: TypeId, target: TypeId) extends FinalDefinition

  case class Identifier(id: TypeId, fields: Seq[Field]) extends FinalDefinition

  case class Interface(id: TypeId, ownFields: Seq[Field], interfaces: Seq[TypeId]) extends FinalDefinition

  case class DTO(id: TypeId, interfaces: Seq[TypeId]) extends FinalDefinition
}







