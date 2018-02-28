package com.github.pshirshov.izumi.idealingua.model.il

import com.github.pshirshov.izumi.idealingua.model.common.{ExtendedField, Field, TypeId}
import com.github.pshirshov.izumi.idealingua.model.il.FinalDefinition.Composite

case class InterfaceConstructors(
                                  impl: TypeId
                                  , missingInterfaces: Composite
                                  , allFields: List[ExtendedField]
                                  , thisFields: Set[Field]
                                  , otherFields: Set[ExtendedField]
                                  , conflicts: FieldConflicts
                                )
