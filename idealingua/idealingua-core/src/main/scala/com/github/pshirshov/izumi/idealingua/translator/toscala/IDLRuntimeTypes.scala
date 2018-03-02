package com.github.pshirshov.izumi.idealingua.translator.toscala

import com.github.pshirshov.izumi.idealingua.model.common.TypeId
import com.github.pshirshov.izumi.idealingua.model.il.ILAst._
import com.github.pshirshov.izumi.idealingua.model.il.{DomainDefinition, DomainId, ILAst}
import com.github.pshirshov.izumi.idealingua.model.runtime.model._

object IDLRuntimeTypes {
  private final val commonsPkg = classOf[TypeId].getPackage.getName
  private final val basePkgParts = commonsPkg.split('.').toSeq.init

  final val basePkg = basePkgParts.mkString(".")
  final val conv = new ScalaTypeConverter(DomainId(basePkgParts.init, basePkgParts.last))

  final val typeId = conv.toScala[TypeId]
  final val typeInfo = conv.toScala[IDLTypeInfo]
  final val idtGenerated = conv.toScala[IDLGeneratedType].init()
  final val idtService = conv.toScala[IDLService].init()
  final val inputInit = conv.toScala[IDLInput].init()
  final val outputInit = conv.toScala[IDLOutput].init()
  final val typeCompanionInit = conv.toScala[IDLTypeCompanion].init()
  final val enumInit = conv.toScala[IDLEnum].init()
  final val enumElInit = conv.toScala[IDLEnumElement].init()
  final val adtInit = conv.toScala[IDLAdt].init()
  final val adtElInit = conv.toScala[IDLAdtElement].init()

  final val serviceCompanionInit = conv.toScala[IDLServiceCompanion].init()

  final val tIDLIdentifier = conv.toScala[IDLIdentifier]
  final val tFinalDefinition = conv.toScala[ILAst]
  final val tDomainCompanion = conv.toScala[IDLDomainCompanion]
  final val tDomainDefinition = conv.toScala[DomainDefinition]
  final val tService = conv.toScala[Service]

}
