package com.github.pshirshov.izumi.idealingua.model.common

case class Field(typeId: TypeId, name: String)
case class ExtendedField(field: Field, definedBy: TypeId)


