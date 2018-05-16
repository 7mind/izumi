package com.github.pshirshov.izumi.idealingua.model.typespace.structures

import com.github.pshirshov.izumi.idealingua.model.common.{SigParam, SigParamSource, StructureId}

final case class ConverterDef(
                               typeToConstruct: StructureId
                               , allFields: List[SigParam]
                               , outerParams: List[SigParamSource]
                             )
