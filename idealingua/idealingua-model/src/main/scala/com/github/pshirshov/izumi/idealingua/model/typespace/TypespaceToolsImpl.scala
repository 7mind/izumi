package com.github.pshirshov.izumi.idealingua.model.typespace

import com.github.pshirshov.izumi.idealingua.model.common.{SigParam, StructureId, TypeId}
import com.github.pshirshov.izumi.idealingua.model.typespace.structures.ConverterDef

class TypespaceToolsImpl() extends TypespaceTools {
  def idToParaName(id: TypeId): String = id.name.toLowerCase

  def mkConverter(innerFields: List[SigParam], outerFields: List[SigParam], targetId: StructureId): ConverterDef = {
    val allFields = innerFields ++ outerFields
    val outerParams = outerFields.map(_.source).distinct

    assert(innerFields.groupBy(_.targetFieldName).forall(_._2.size == 1), s"$targetId: Contradictive inner fields: ${innerFields.mkString("\n  ")}")
    assert(outerFields.groupBy(_.targetFieldName).forall(_._2.size == 1), s"$targetId: Contradictive outer fields: ${outerFields.mkString("\n  ")}")
    assert(allFields.groupBy(_.targetFieldName).forall(_._2.size == 1), s"$targetId: Contradictive fields: ${allFields.mkString("\n  ")}")
    assert(outerParams.groupBy(_.sourceName).forall(_._2.size == 1), s"$targetId: Contradictive outer params: ${outerParams.mkString("\n  ")}")

    // TODO: pass definition instead of id
    ConverterDef(
      targetId
      , allFields
      , outerParams
    )
  }

}
