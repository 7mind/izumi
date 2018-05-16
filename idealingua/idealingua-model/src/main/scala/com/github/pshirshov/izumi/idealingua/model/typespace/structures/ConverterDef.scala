package com.github.pshirshov.izumi.idealingua.model.typespace.structures

import com.github.pshirshov.izumi.idealingua.model.common.StructureId
import com.github.pshirshov.izumi.idealingua.model.typespace.{ParamSource, ParamX}

final case class ConverterDef(
                               typeToConstruct: StructureId
                               , allFields: List[ParamX]
                               , outerParams: List[ParamSource]
                             )
