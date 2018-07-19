package com.github.pshirshov.izumi.idealingua.model.il.ast.raw

import com.github.pshirshov.izumi.idealingua.model.common.AbstractIndefiniteId


final case class RawField(typeId: AbstractIndefiniteId, name: String)


final case class RawSimpleStructure(concepts: RawStructures, fields: RawTuple)


final case class RawStructure(interfaces: RawInterfaces, concepts: RawStructures, removedConcepts: RawStructures, fields: RawTuple, removedFields: RawTuple) {
  def extend(other: RawStructure): RawStructure = {
    this.copy(
      interfaces = interfaces ++ other.interfaces
      , concepts = concepts ++ other.concepts
      , removedConcepts = removedConcepts ++ other.removedConcepts
      , fields = fields ++ other.fields
      , removedFields= removedFields++ other.removedFields
    )
  }
}
