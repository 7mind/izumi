package com.github.pshirshov.izumi.idealingua.model.common

import com.github.pshirshov.izumi.idealingua.model.il.ast.typed

final case class FieldDef(definedBy: TypeId, usedBy: TypeId, distance: Int)

final case class ExtendedField(field: typed.Field, defn: FieldDef) {
  override def toString: TypeName = s"${defn.definedBy}#$field: ${defn.usedBy}"
}
