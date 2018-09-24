package com.github.pshirshov.izumi.idealingua.model.typespace

import com.github.pshirshov.izumi.fundamentals.platform.language.Quirks
import com.github.pshirshov.izumi.idealingua.model.common.TypeId.{AdtId, DTOId, InterfaceId}
import com.github.pshirshov.izumi.idealingua.model.common.{StructureId, TypeId}
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.DefMethod.RPCMethod

class TypespaceToolsImpl(ts: Typespace) extends TypespaceTools {
  val methodOutputSuffix = "Output"
  val methodInputSuffix = "Input"

  val goodAltBranchName = "Success"
  val badAltBranchName = "Failure"

  val goodAltSuffix = "Success"
  val badAltSuffix = "Failure"

  def idToParaName(id: TypeId): String = id.name.toLowerCase

  override def implId(id: InterfaceId): DTOId = {
    DTOId(id, toDtoName(id))
  }

  override def sourceId(id: DTOId): Option[InterfaceId] = {
    // TODO We can probably skip isEphemeral in this case since map will give an option anyway?
    if (ts.types.isInterfaceEphemeral(id)) {
      ts.types.interfaceEphemeralsReversed.get(id)
    } else {
      None
    }
  }

  override def defnId(id: StructureId): InterfaceId = {
    id match {
      case d: DTOId if ts.types.isInterfaceEphemeral(d) =>
        ts.types.interfaceEphemeralsReversed(d)

      case d: DTOId =>
        InterfaceId(d, toInterfaceName(d))

      case i: InterfaceId =>
        i
    }
  }

  def methodToOutputName(method: RPCMethod): String = {
    s"${method.name.capitalize}$methodOutputSuffix"
  }

  def methodToPositiveTypeName(method: RPCMethod): String = {
    s"${method.name.capitalize}$goodAltSuffix"
  }

  def methodToNegativeTypeName(method: RPCMethod): String = {
    s"${method.name.capitalize}$badAltSuffix"
  }


  def toPositiveBranchName(id: AdtId): String = {
    Quirks.discard(id)
    goodAltBranchName
  }

  def toNegativeBranchName(id: AdtId): String = {
    Quirks.discard(id)
    badAltBranchName
  }


  def toDtoName(id: TypeId): String = {
    id match {
      case _: InterfaceId =>
        //s"${id.name}Struct"
        "Struct"
      case _ =>
        s"${id.name}"

    }
  }

  def toInterfaceName(id: TypeId): String = {
    id match {
      case _: DTOId =>
        //s"${id.name}Defn"

        "Defn"
      case _ =>
        s"${id.name}"

    }
  }

}
