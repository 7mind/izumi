package com.github.pshirshov.izumi.idealingua.model.il

import com.github.pshirshov.izumi.idealingua.model.common.{ExtendedField, StructureId, TypeId}
import com.github.pshirshov.izumi.idealingua.model.il.ILAst.{Composite, Field}

case class InterfaceConstructors(
                                  typeToConstruct: StructureId
                                  , parentsAsParams: Composite
                                  , parentInstanceFields: Set[Field]
                                  , mixinsInstancesFields: Set[ExtendedField]
                                  , localFields: Set[ExtendedField]

                                  , fields: Struct
                                )
