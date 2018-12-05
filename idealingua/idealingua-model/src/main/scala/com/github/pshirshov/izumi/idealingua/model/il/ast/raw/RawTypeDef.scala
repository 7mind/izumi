package com.github.pshirshov.izumi.idealingua.model.il.ast.raw

import com.github.pshirshov.izumi.idealingua.model.common.TypeId._
import com.github.pshirshov.izumi.idealingua.model.common.{AbstractIndefiniteId, TypeId}
import com.github.pshirshov.izumi.idealingua.model.il.ast.InputPosition
import com.github.pshirshov.izumi.idealingua.model.loader.FSPath


trait RawPositioned {
  def updatePosition(start: Int, stop: Int, location: FSPath): RawPositioned

}

sealed trait RawTypeDef extends RawPositioned {
  def meta: RawNodeMeta
  def updateMeta(f: RawNodeMeta => RawNodeMeta): RawTypeDef

  override def updatePosition(start: Int, stop: Int, location: FSPath): RawTypeDef = {
    updateMeta {
      meta =>
        meta.copy(position = InputPosition.Defined(start, stop, location))
    }
  }
}

sealed trait IdentifiedRawTypeDef extends RawTypeDef {
  def id: TypeId
}


case class RawNodeMeta(doc: Option[String], annos: Seq[RawAnno], position: InputPosition = InputPosition.Undefined)

object RawTypeDef {

  final case class Interface(id: InterfaceId, struct: RawStructure, meta: RawNodeMeta) extends IdentifiedRawTypeDef {
    override def updateMeta(f: RawNodeMeta => RawNodeMeta): Interface = this.copy(meta = f(meta))
  }

  final case class DTO(id: DTOId, struct: RawStructure, meta: RawNodeMeta) extends IdentifiedRawTypeDef {
    override def updateMeta(f: RawNodeMeta => RawNodeMeta): DTO = this.copy(meta = f(meta))
  }

  final case class Enumeration(id: EnumId, members: List[String], meta: RawNodeMeta) extends IdentifiedRawTypeDef {
    override def updateMeta(f: RawNodeMeta => RawNodeMeta): Enumeration = this.copy(meta = f(meta))
  }

  final case class Alias(id: AliasId, target: AbstractIndefiniteId, meta: RawNodeMeta) extends IdentifiedRawTypeDef {
    override def updateMeta(f: RawNodeMeta => RawNodeMeta): Alias = this.copy(meta = f(meta))
  }

  final case class Identifier(id: IdentifierId, fields: RawTuple, meta: RawNodeMeta) extends IdentifiedRawTypeDef {
    override def updateMeta(f: RawNodeMeta => RawNodeMeta): Identifier = this.copy(meta = f(meta))
  }

  final case class Adt(id: AdtId, alternatives: List[RawAdtMember], meta: RawNodeMeta) extends IdentifiedRawTypeDef {
    override def updateMeta(f: RawNodeMeta => RawNodeMeta): Adt = this.copy(meta = f(meta))
  }

  final case class NewType(id: ParsedId, source: AbstractIndefiniteId, modifiers: Option[RawStructure], meta: RawNodeMeta) extends RawTypeDef {
    override def updateMeta(f: RawNodeMeta => RawNodeMeta): NewType = this.copy(meta = f(meta))
  }

}


case class RawAnno(name: String, values: RawVal.CMap)
