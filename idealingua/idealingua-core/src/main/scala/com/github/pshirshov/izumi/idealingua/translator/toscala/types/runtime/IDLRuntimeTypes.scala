package com.github.pshirshov.izumi.idealingua.translator.toscala.types.runtime

import com.github.pshirshov.izumi.idealingua.model.common.TypeId
import com.github.pshirshov.izumi.idealingua.model.il.{DomainDefinition, DomainId, ILAst}
import com.github.pshirshov.izumi.idealingua.runtime.model._
import com.github.pshirshov.izumi.idealingua.runtime.model.introspection.{IDLDomainCompanion, IDLTypeInfo, WithInfo}
import com.github.pshirshov.izumi.idealingua.runtime.transport.{AbstractClientDispatcher, AbstractServerDispatcher}
import com.github.pshirshov.izumi.idealingua.translator.toscala.types.ScalaTypeConverter

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

  final val generated = modelConv.toScala[IDLGeneratedType]

  final val enum = modelConv.toScala[IDLEnum]
  final val enumEl = modelConv.toScala[IDLEnumElement]

  final val tIDLIdentifier = modelConv.toScala[IDLIdentifier]

  final val adt = modelConv.toScala[IDLAdt]
  final val adtEl = modelConv.toScala[IDLAdtElement]

  final val idtService = modelConv.toScala[IDLService[Id]]
  final val serverDispatcher = runtimeConv.toScala[AbstractServerDispatcher[Id, _]]
  final val clientDispatcher = runtimeConv.toScala[AbstractClientDispatcher[Id, _]]
  final val clientWrapper = runtimeConv.toScala[IDLClientWrapper[Id, _]]
  final val serverWrapper = runtimeConv.toScala[IDLServiceExploded[Id, _]]
  final val input = modelConv.toScala[IDLInput]
  final val output = modelConv.toScala[IDLOutput]

  // introspection
  final val typeId = modelConv.toScala[TypeId]
  final val typeInfo = modelConv.toScala[IDLTypeInfo]
  final val withTypeInfo = modelConv.toScala[WithInfo]
  final val tFinalDefinition = modelConv.toScala[ILAst]
  final val tDomainDefinition = modelConv.toScala[DomainDefinition]
  final val tDomainId = modelConv.toScala[DomainId]
  final val tDomainCompanion = modelConv.toScala[IDLDomainCompanion]

}
