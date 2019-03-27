package com.github.pshirshov.izumi.idealingua.model.common

import com.github.pshirshov.izumi.idealingua.model.il.ast.typed
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.Field


final case class FieldDef(
                           definedBy: TypeId
                           , definedWithIndex: Int
                           , usedBy: TypeId
                           , distance: Int
                           , variance: List[Field] = List.empty
                         )

final case class ExtendedField(field: typed.Field, defn: FieldDef) {
  override def toString: TypeName = s"${defn.usedBy}#$field <- ${defn.definedBy}.${defn.definedWithIndex} (distance: ${defn.distance})"
}
