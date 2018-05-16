package com.github.pshirshov.izumi.idealingua.model.typespace.structures

import com.github.pshirshov.izumi.idealingua.model.common.{ExtendedField, StructureId}
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.Field

final case class ConverterDef(
                                  typeToConstruct: StructureId
                                  , parentInstanceFields: Set[Field]
                                  , localFields: Set[Field]
                                  , mixinsInstancesFields: List[ExtendedField]
                                )
