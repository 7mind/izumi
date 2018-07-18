package com.github.pshirshov.izumi.idealingua.model.typespace

import com.github.pshirshov.izumi.idealingua.model.common.TypeId.{DTOId, InterfaceId}
import com.github.pshirshov.izumi.idealingua.model.common.{SigParam, StructureId, TypeId}
import com.github.pshirshov.izumi.idealingua.model.typespace.structures.ConverterDef
import com.github.pshirshov.izumi.fundamentals.platform.strings.IzString._

class TypespaceToolsImpl(types: TypeCollection) extends TypespaceTools {
  def idToParaName(id: TypeId): String = id.name.toLowerCase

  def mkConverter(innerFields: List[SigParam], outerFields: List[SigParam], targetId: StructureId): ConverterDef = {
    val allFields = innerFields ++ outerFields
    val outerParams = outerFields.map(_.source).distinct
    assert(innerFields.groupBy(_.targetFieldName).forall(_._2.size == 1), s"$targetId: Contradictive inner fields: ${innerFields.niceList()}")
    assert(outerFields.groupBy(_.targetFieldName).forall(_._2.size == 1), s"$targetId: Contradictive outer fields: ${outerFields.niceList()}")
    assert(allFields.groupBy(_.targetFieldName).forall(_._2.size == 1), s"$targetId: Contradictive fields: ${allFields.niceList()}")
    assert(outerParams.groupBy(_.sourceName).forall(_._2.size == 1), s"$targetId: Contradictive outer params: ${outerParams.niceList()}")

    // TODO: pass definition instead of id
    ConverterDef(
      targetId
      , allFields
      , outerParams
    )
  }

  override def implId(id: InterfaceId): DTOId = {
    DTOId(id, types.toDtoName(id))
  }

  override def sourceId(id: DTOId): Option[InterfaceId] = {
    // TODO We can probably skip isEphemeral in this case since map will give an option anyway?
    if (types.isInterfaceEphemeral(id)) {
      types.interfaceEphemeralsReversed.get(id)
    } else {
      None
    }
  }

  override def defnId(id: StructureId): InterfaceId = {
    id match {
      case d: DTOId if types.isInterfaceEphemeral(d) =>
        types.interfaceEphemeralsReversed(d)

      case d: DTOId =>
        InterfaceId(d, types.toInterfaceName(d))

      case i: InterfaceId =>
        i
    }
  }
}
