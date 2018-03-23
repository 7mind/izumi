package com.github.pshirshov.izumi.idealingua.translator.toscala

import com.github.pshirshov.izumi.idealingua.model.common.TypeId
import com.github.pshirshov.izumi.idealingua.model.il.ILAst._
import com.github.pshirshov.izumi.idealingua.model.il.{DomainDefinition, DomainId, ILAst}
import com.github.pshirshov.izumi.idealingua.runtime.model._
import com.github.pshirshov.izumi.idealingua.runtime.model.introspection.{IDLDomainCompanion, IDLTypeInfo, WithInfo}
import com.github.pshirshov.izumi.idealingua.runtime.transport.{AbstractClientDispatcher, AbstractServerDispatcher}

object IDLRuntimeTypes {
  private final val commonsPkg = classOf[TypeId].getPackage.getName
  private final val basePkgParts = commonsPkg.split('.').toSeq.init
  final val modelPkg = basePkgParts.mkString(".")
  final val modelConv = new ScalaTypeConverter(DomainId(basePkgParts.init, basePkgParts.last))

  private final val transportPkg = classOf[AbstractClientDispatcher[Id, _]].getPackage.getName
  private final val runtimePkgParts = transportPkg.split('.').toSeq.init
  final val runtimePkg = runtimePkgParts.mkString(".")
  final val runtimeConv = new ScalaTypeConverter(DomainId(runtimePkgParts.init, runtimePkgParts.last))


  private type Id[R] = R
  final val idtService = modelConv.toScala[IDLService[Id]]
  final val typeId = modelConv.toScala[TypeId]
  final val typeInfo = modelConv.toScala[IDLTypeInfo]
  final val withTypeInfo = modelConv.toScala[WithInfo]
  final val idtGenerated = modelConv.toScala[IDLGeneratedType].init()
  final val inputInit = modelConv.toScala[IDLInput].init()
  final val outputInit = modelConv.toScala[IDLOutput].init()
  final val typeCompanionInit = modelConv.toScala[IDLTypeCompanion].init()
  final val enumInit = modelConv.toScala[IDLEnum].init()
  final val enumElInit = modelConv.toScala[IDLEnumElement].init()
  final val adtInit = modelConv.toScala[IDLAdt].init()
  final val adtElInit = modelConv.toScala[IDLAdtElement].init()

  final val serviceCompanionInit = modelConv.toScala[IDLServiceCompanion].init()

  final val tIDLIdentifier = modelConv.toScala[IDLIdentifier]
  final val tFinalDefinition = modelConv.toScala[ILAst]
  final val tDomainCompanion = modelConv.toScala[IDLDomainCompanion]
  final val tAdtElementCompanion = modelConv.toScala[IDLAdtElementCompanion]
  final val tDomainDefinition = modelConv.toScala[DomainDefinition]
  final val tDomainId = modelConv.toScala[DomainId]
  final val tService = modelConv.toScala[Service]

  final val serverDispatcher = runtimeConv.toScala[AbstractServerDispatcher[Id, _]]
  final val clientDispatcher = runtimeConv.toScala[AbstractClientDispatcher[Id, _]]
  final val clientWrapper = runtimeConv.toScala[IDLClientWrapper[Id, _]]
}
