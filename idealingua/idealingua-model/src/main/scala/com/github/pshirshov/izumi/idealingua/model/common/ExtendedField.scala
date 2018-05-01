package com.github.pshirshov.izumi.idealingua.model.common

import com.github.pshirshov.izumi.idealingua.model.il.ast.typed

final case class ExtendedField(field: typed.Field, definedBy: TypeId) {
  override def toString: TypeName = s"$definedBy#$field"
}
