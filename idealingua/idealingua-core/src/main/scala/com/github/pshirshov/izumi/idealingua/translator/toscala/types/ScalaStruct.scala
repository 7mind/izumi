package com.github.pshirshov.izumi.idealingua.translator.toscala.types

import com.github.pshirshov.izumi.idealingua.model.common.StructureId
import com.github.pshirshov.izumi.idealingua.model.il.{AbstractStruct, Struct}

class ScalaStruct
(
  _unique: List[ScalaField]
  , _nonUnique: List[ScalaField]
  , _fields: Struct
) extends AbstractStruct[ScalaField] {
  def id: StructureId = _fields.id

  def fields: Struct = _fields

  def unique: List[ScalaField] = _unique

  def nonUnique: List[ScalaField] = _nonUnique

  def all: List[ScalaField] = _unique ++ _nonUnique

  override protected def isLocal(f: ScalaField): Boolean = {
    f.field.definedBy == _fields.id
  }
}
