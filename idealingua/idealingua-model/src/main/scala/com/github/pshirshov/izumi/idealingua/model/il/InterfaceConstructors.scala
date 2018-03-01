package com.github.pshirshov.izumi.idealingua.model.il

import com.github.pshirshov.izumi.idealingua.model.common.{ExtendedField, Field, TypeId}
import com.github.pshirshov.izumi.idealingua.model.il.FinalDefinition.Composite

case class InterfaceConstructors(
                                  typeToConstruct: TypeId
                                  , requiredParameters: Composite
                                  , fieldsToCopyFromInterface: Set[Field]
                                  , fieldsToTakeFromParameters: Set[ExtendedField]
                                  , conflicts: FieldConflicts
                                )
