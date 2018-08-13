package com.github.pshirshov.izumi.idealingua.translator.toscala.types.runtime

import com.github.pshirshov.izumi.idealingua.model.common.{DomainId, TypeId}
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.{DomainDefinition, TypeDef}
import com.github.pshirshov.izumi.idealingua.runtime.{IRTCast, IRTConversions, IRTExtend}
import com.github.pshirshov.izumi.idealingua.runtime.model._
import com.github.pshirshov.izumi.idealingua.runtime.model.introspection.{IDLDomainCompanion, IDLTypeInfo, IDLWithInfo}
import com.github.pshirshov.izumi.idealingua.runtime.rpc.{IRTMethodName, IRTMethodSignature, IRTResultTransZio, IRTWrappedService, _}


object IDLRuntimeTypes {

  val model: Pkg = Pkg.parentOf[TypeId]
  val services: Pkg = Pkg.of[IRTResult[_2Arg]]

  type _2Arg[X, Y] = Nothing
  type _1Arg[R] = Nothing
  type _0Arg = Nothing

  final val generated = model.conv.toScala[IDLGeneratedType]

  final val enum = model.conv.toScala[IDLEnum]
  final val enumEl = model.conv.toScala[IDLEnumElement]

  final val tIDLIdentifier = model.conv.toScala[IDLIdentifier]

  final val adt = model.conv.toScala[IDLAdt]
  final val adtEl = model.conv.toScala[IDLAdtElement]

  final val WithResult = services.conv.toScala[IRTResult[_2Arg]]
  final val WithResultZio = services.conv.toScala[IRTResultZio]
  final val IRTResultTransZio = services.conv.toScala[IRTResultTransZio[_2Arg]]
  final val IRTMethodSignature = services.conv.toScala[IRTMethodSignature]
  final val IRTServiceId = services.conv.toScala[IRTServiceId]
  final val IRTMethodId = services.conv.toScala[IRTMethodId]
  final val IRTMethodName = services.conv.toScala[IRTMethodName]
  final val IRTWrappedClient = services.conv.toScala[IRTWrappedClient[_2Arg]]
  final val IRTWrappedService = services.conv.toScala[IRTWrappedService[_2Arg, _0Arg]]
  final val IRTDispatcher = services.conv.toScala[IRTDispatcher]
  final val Conversions = model.conv.toScala[IRTConversions[_0Arg]]
  final val Cast = model.conv.toScala[IRTCast[_0Arg, _0Arg]]
  final val Extend = model.conv.toScala[IRTExtend[_0Arg, _0Arg]]

  // introspection
  final val typeId = model.conv.toScala[TypeId]
  final val typeInfo = model.conv.toScala[IDLTypeInfo]
  final val withTypeInfo = model.conv.toScala[IDLWithInfo]
  final val tFinalDefinition = model.conv.toScala[TypeDef]
  final val tDomainDefinition = model.conv.toScala[DomainDefinition]
  final val tDomainId = model.conv.toScala[DomainId]
  final val tDomainCompanion = model.conv.toScala[IDLDomainCompanion]

}
