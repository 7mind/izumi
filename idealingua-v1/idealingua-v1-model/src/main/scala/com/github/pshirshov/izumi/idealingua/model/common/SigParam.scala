package com.github.pshirshov.izumi.idealingua.model.common

case class SigParamSource(sourceType: TypeId, sourceName: String)

case class SigParam(targetFieldName: String, source: SigParamSource, sourceFieldName: Option[String])
