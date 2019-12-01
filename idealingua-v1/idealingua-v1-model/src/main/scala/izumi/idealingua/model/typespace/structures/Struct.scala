package izumi.idealingua.model.typespace.structures

import izumi.idealingua.model.common.TypeId.InterfaceId
import izumi.idealingua.model.common.{ExtendedField, StructureId}
import izumi.idealingua.model.il.ast.typed.Super


class Struct
(
  val id: StructureId
  , val superclasses: Super
  , val unambigious: List[ExtendedField]
  , val ambigious: List[ExtendedField]
  , val all: List[ExtendedField] // keeping separatedly to preserve order
) extends ConstAbstractStruct[ExtendedField] {
  override protected def isLocal(f: ExtendedField): Boolean = {
    f.defn.definedBy == id
  }

  def requiredInterfaces: List[InterfaceId] = {
    all
      .map(_.defn.definedBy)
      .collect { case i: InterfaceId => i }
      .distinct
  }
}

final case class PlainStruct(all: List[ExtendedField])

