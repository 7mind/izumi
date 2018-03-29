package com.github.pshirshov.izumi.idealingua.translator.toscala.types

import com.github.pshirshov.izumi.idealingua.model.common.StructureId
import com.github.pshirshov.izumi.idealingua.model.il.{AbstractStruct, Struct}

class ScalaStruct
(
  val fields: Struct
  , unambigious: List[ScalaField]
  , ambigious: List[ScalaField]
) extends AbstractStruct[ScalaField] {
  def id: StructureId = fields.id

  def all: List[ScalaField] = unambigious ++ ambigious

  def localOrAmbigious: List[ScalaField] = (ambigious ++ local).distinct

  override protected def isLocal(f: ScalaField): Boolean = {
    f.field.definedBy == id
  }
}
