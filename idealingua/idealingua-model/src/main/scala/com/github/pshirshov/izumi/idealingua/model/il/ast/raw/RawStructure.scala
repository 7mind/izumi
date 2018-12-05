package com.github.pshirshov.izumi.idealingua.model.il.ast.raw

import com.github.pshirshov.izumi.idealingua.model.common.AbstractIndefiniteId
import com.github.pshirshov.izumi.idealingua.model.il.ast.InputPosition


final case class RawField(typeId: AbstractIndefiniteId, name: String, position: InputPosition = InputPosition.Undefined) extends RawPositioned {
  override def updatePosition(position: ParserPosition[_]): RawPositioned = this.copy(position = position.toInputPos)
}


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
