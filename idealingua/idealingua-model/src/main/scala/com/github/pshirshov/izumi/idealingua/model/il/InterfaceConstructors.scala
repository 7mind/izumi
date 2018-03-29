package com.github.pshirshov.izumi.idealingua.model.il

import com.github.pshirshov.izumi.idealingua.model.common.{ExtendedField, StructureId}
import com.github.pshirshov.izumi.idealingua.model.il.ILAst.Field

case class InterfaceConstructors(
                                  typeToConstruct: StructureId
                                  , parentInstanceFields: Set[Field]
                                  , localFields: Set[Field]
                                  , mixinsInstancesFields: Set[ExtendedField]
                                )
