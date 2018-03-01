package com.github.pshirshov.izumi.idealingua.model.il

import com.github.pshirshov.izumi.idealingua.model.common.{ExtendedField, TypeId}
import com.github.pshirshov.izumi.idealingua.model.il.ILAst.{Composite, Field}

case class InterfaceConstructors(
                                  typeToConstruct: TypeId
                                  , requiredParameters: Composite
                                  , fieldsToCopyFromInterface: Set[Field]
                                  , fieldsToTakeFromParameters: Set[ExtendedField]
                                  , conflicts: FieldConflicts
                                )
