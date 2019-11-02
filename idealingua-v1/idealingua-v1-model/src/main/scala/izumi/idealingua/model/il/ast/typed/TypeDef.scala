package izumi.idealingua.model.il.ast.typed

import izumi.idealingua.model.common.TypeId._
import izumi.idealingua.model.common._


sealed trait TypeDef {
  def id: TypeId

  def meta: NodeMeta
}

case class EnumMember(value: String, meta: NodeMeta)

object TypeDef {

  final case class Alias(id: AliasId, target: TypeId, meta: NodeMeta) extends TypeDef

  final case class Enumeration(id: EnumId, members: List[EnumMember], meta: NodeMeta) extends TypeDef

  final case class Adt(id: AdtId, alternatives: List[AdtMember], meta: NodeMeta) extends TypeDef

  final case class Identifier(id: IdentifierId, fields: IdTuple, meta: NodeMeta) extends TypeDef

  sealed trait WithStructure extends TypeDef {
    def id: StructureId

    def struct: Structure
  }

  final case class Interface(id: InterfaceId, struct: Structure, meta: NodeMeta) extends WithStructure

  final case class DTO(id: DTOId, struct: Structure, meta: NodeMeta) extends WithStructure

  //final case class ForeignType(id: IndefiniteId, mapping: Map[String, String], meta: NodeMeta) extends TypeDef

}

