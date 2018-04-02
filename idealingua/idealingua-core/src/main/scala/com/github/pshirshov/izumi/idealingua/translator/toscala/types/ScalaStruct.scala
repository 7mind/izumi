package com.github.pshirshov.izumi.idealingua.translator.toscala.types

import com.github.pshirshov.izumi.idealingua.model.common.StructureId
import com.github.pshirshov.izumi.idealingua.model.il.structures.{ConstAbstractStruct, Struct}

class ScalaStruct
(
  val fields: Struct
  , val unambigious: List[ScalaField]
  , val ambigious: List[ScalaField]
) extends ConstAbstractStruct[ScalaField] {

  val id: StructureId = fields.id

  override lazy val all: List[ScalaField] = unambigious ++ ambigious

  override protected def isLocal(f: ScalaField): Boolean = {
    f.field.definedBy == id
  }
}


case class PlainScalaStruct(all: List[ScalaField])


