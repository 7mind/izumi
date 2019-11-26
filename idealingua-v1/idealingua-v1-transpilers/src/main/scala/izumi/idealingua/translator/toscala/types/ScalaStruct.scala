package izumi.idealingua.translator.toscala.types

import izumi.idealingua.model.common.StructureId
import izumi.idealingua.model.typespace.structures.{ConstAbstractStruct, Struct}

class ScalaStruct
(
  val fields: Struct
  , val unambigious: List[ScalaField]
  , val ambigious: List[ScalaField]
  , val all: List[ScalaField] // keeping separatedly to preserve order
) extends ConstAbstractStruct[ScalaField] {

  val id: StructureId = fields.id

  override protected def isLocal(f: ScalaField): Boolean = {
    f.field.defn.definedBy == id
  }
}


final case class PlainScalaStruct(all: List[ScalaField])


