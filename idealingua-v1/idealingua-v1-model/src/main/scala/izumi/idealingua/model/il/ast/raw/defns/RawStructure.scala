package izumi.idealingua.model.il.ast.raw.defns

import izumi.idealingua.model.common.TypeId.{DTOId, InterfaceId}
import izumi.idealingua.model.common.{IndefiniteMixin, TypeId}
import izumi.idealingua.model.il.ast.raw.defns.RawTypeDef.{DTO, Interface}

final case class RawStructure(interfaces: RawInterfaces, concepts: RawStructures, removedConcepts: RawStructures, fields: RawTuple, removedFields: RawTuple) {
  def extend(other: RawStructure): RawStructure = {
    this.copy(
      interfaces = interfaces ++ other.interfaces
      , concepts = concepts ++ other.concepts
      , removedConcepts = removedConcepts ++ other.removedConcepts
      , fields = fields ++ other.fields
      , removedFields = removedFields ++ other.removedFields
    )
  }
}

object RawStructure {
  sealed trait StructOp

  object StructOp {

    final case class Extend(tpe: TypeId.InterfaceId) extends StructOp

    final case class Mix(tpe: IndefiniteMixin) extends StructOp

    final case class Drop(tpe: IndefiniteMixin) extends StructOp

    final case class AddField(field: RawField) extends StructOp

    final case class RemoveField(field: RawField) extends StructOp

  }

  final case class Aux(structure: RawStructure) {
    def toInterface(id: InterfaceId, meta: RawNodeMeta): RawTypeDef.Interface = {
      Interface(id, structure, meta)
    }

    def toDto(id: DTOId, meta: RawNodeMeta): RawTypeDef.DTO = {
      DTO(id, structure, meta)
    }
  }

  object Aux {
    def apply(v: Seq[StructOp]): Aux = {
      import StructOp._
      Aux(RawStructure(
        v.collect { case Extend(i) => i }.toList
        , v.collect { case Mix(i) => i }.toList
        , v.collect { case Drop(i) => i }.toList
        , v.collect { case AddField(i) => i }.toList
        , v.collect { case RemoveField(i) => i }.toList
      ))
    }
  }

}
