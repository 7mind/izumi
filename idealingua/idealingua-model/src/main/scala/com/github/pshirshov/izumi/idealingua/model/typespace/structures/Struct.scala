package com.github.pshirshov.izumi.idealingua.model.typespace.structures

import com.github.pshirshov.izumi.idealingua.model.common.TypeId.InterfaceId
import com.github.pshirshov.izumi.idealingua.model.common.{ExtendedField, StructureId}
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.Super


class Struct
(
  val id: StructureId
  , val superclasses: Super
  , val unambigious: List[ExtendedField]
  , val ambigious: List[ExtendedField]
) extends ConstAbstractStruct[ExtendedField] {

  override def all: List[ExtendedField] = unambigious ++ ambigious

  override protected def isLocal(f: ExtendedField): Boolean = {
    f.definedBy == id
  }

  def requiredInterfaces: List[InterfaceId] = {
    all
      .map(_.definedBy)
      .collect({ case i: InterfaceId => i })
      .distinct
  }
}

final case class PlainStruct(all: List[ExtendedField])

