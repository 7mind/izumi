package com.github.pshirshov.izumi.idealingua.model.il.ast.typed

import com.github.pshirshov.izumi.idealingua.model.common.TypeId._
import com.github.pshirshov.izumi.idealingua.model.common._
import com.github.pshirshov.izumi.idealingua.model.il.ast.InputPosition

sealed trait Value

object Value {

  final case class CInt(value: Int) extends Value
  final case class CLong(value: Long) extends Value
  final case class CFloat(value: Double) extends Value
  final case class CString(value: String) extends Value
  final case class CBool(value: Boolean) extends Value
  final case class CMap(value: Map[String, Value]) extends Value
  final case class CList(value: List[Value]) extends Value

  sealed trait Typed extends Value {
    def typeId: TypeId
  }

  final case class CTypedList(typeId: TypeId, value: CList) extends Typed
  final case class CTypedObject(typeId: TypeId, value: CMap) extends Typed
  final case class CTyped(typeId: TypeId, value: Value) extends Typed

}

case class Anno(name: String, values: Map[String, Value], position: InputPosition)


case class NodeMeta(doc: Option[String], annos: Seq[Anno], pos: InputPosition)

object NodeMeta {
  final val empty: NodeMeta = NodeMeta(None, Seq.empty, InputPosition.Undefined)
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














