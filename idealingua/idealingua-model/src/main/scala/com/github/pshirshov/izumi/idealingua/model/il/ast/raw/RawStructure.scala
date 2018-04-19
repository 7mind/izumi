package com.github.pshirshov.izumi.idealingua.model.il.ast.raw

import com.github.pshirshov.izumi.idealingua.model.common.AbstractIndefiniteId


case class RawField(typeId: AbstractIndefiniteId, name: String)


case class RawSimpleStructure(concepts: RawInterfaces, fields: RawTuple)


case class RawStructure(interfaces: RawInterfaces, concepts: RawInterfaces, removedConcepts: RawInterfaces, fields: RawTuple, removedFields: RawTuple)
