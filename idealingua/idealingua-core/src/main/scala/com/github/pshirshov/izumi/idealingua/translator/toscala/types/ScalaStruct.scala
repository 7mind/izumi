package com.github.pshirshov.izumi.idealingua.translator.toscala.types

import com.github.pshirshov.izumi.idealingua.model.common.StructureId
import com.github.pshirshov.izumi.idealingua.model.il.{AbstractStruct, Struct}

class ScalaStruct
(
  val fields: Struct
  , val unambigious: List[ScalaField]
  , val ambigious: List[ScalaField]
) extends AbstractStruct[ScalaField] {

  val id: StructureId = fields.id

  val all: List[ScalaField] = unambigious ++ ambigious


  override protected def isLocal(f: ScalaField): Boolean = {
    f.field.definedBy == id
  }
}
