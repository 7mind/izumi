package com.github.pshirshov.izumi.idealingua.model.il.ast.typed

import com.github.pshirshov.izumi.idealingua.model.common.TypeId._
import com.github.pshirshov.izumi.idealingua.model.common._


case class Anno(name: String, values: Map[String, Any])


case class NodeMeta(doc: Option[String], annos: Seq[Anno])

object NodeMeta {
  final val empty: NodeMeta = NodeMeta(None, Seq.empty)
}

sealed trait TypeDef {
  def id: TypeId
  def meta: NodeMeta
}


object TypeDef {

  final case class Alias(id: AliasId, target: TypeId, meta: NodeMeta) extends TypeDef

  final case class Enumeration(id: EnumId, members: List[String], meta: NodeMeta) extends TypeDef

  final case class Adt(id: AdtId, alternatives: List[AdtMember], meta: NodeMeta) extends TypeDef

  final case class Identifier(id: IdentifierId, fields: IdTuple, meta: NodeMeta) extends TypeDef

  sealed trait WithStructure extends TypeDef {
    def id: StructureId

    def struct: Structure
  }

  final case class Interface(id: InterfaceId, struct: Structure, meta: NodeMeta) extends WithStructure

  final case class DTO(id: DTOId, struct: Structure, meta: NodeMeta) extends WithStructure

}














