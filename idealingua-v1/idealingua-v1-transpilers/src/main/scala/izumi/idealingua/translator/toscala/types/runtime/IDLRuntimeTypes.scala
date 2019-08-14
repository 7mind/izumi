package izumi.idealingua.translator.toscala.types.runtime

import izumi.functional.bio.BIO
import izumi.idealingua.model.common.TypeId
import izumi.idealingua.runtime.model._
import izumi.idealingua.runtime.rpc.{IRTMethodName, IRTMethodSignature, IRTWrappedService, _}
import izumi.idealingua.runtime.{IRTCast, IRTConversions, IRTExtend}
import izumi.idealingua.translator.toscala.types.ScalaType


object IDLRuntimeTypes {

  val model: Pkg = Pkg.of[TypeId]
  val services: Pkg = Pkg.of[IRTServerMultiplexor[_2Arg, _0Arg, _0Arg]]

  type _2Arg[+X, +Y] = Nothing
  type _1Arg[R] = Nothing
  type _0Arg = Nothing

  final val generated = model.conv.toScala[IDLGeneratedType]

  final val enum = model.conv.toScala[IDLEnum]
  final val enumEl = model.conv.toScala[IDLEnumElement]

  final val tIDLIdentifier = model.conv.toScala[IDLIdentifier]

  final val adt = model.conv.toScala[IDLAdt]
  final val adtEl = model.conv.toScala[IDLAdtElement]

  final val WithResult = services.conv.toScala[BIO[_2Arg]]
  final val IRTBio: ScalaType = services.conv.toScala[BIO[_2Arg]]
  final val IRTMethodSignature = services.conv.toScala[IRTMethodSignature]
  final val IRTServiceId = services.conv.toScala[IRTServiceId]
  final val IRTMethodId = services.conv.toScala[IRTMethodId]
  final val IRTMethodName = services.conv.toScala[IRTMethodName]
  final val IRTWrappedClient = services.conv.toScala[IRTWrappedClient]
  final val IRTWrappedService = services.conv.toScala[IRTWrappedService[_2Arg, _0Arg]]
  final val IRTDispatcher = services.conv.toScala[IRTDispatcher[_2Arg]]
  final val Conversions = model.conv.toScala[IRTConversions[_0Arg]]
  final val Cast = model.conv.toScala[IRTCast[_0Arg, _0Arg]]
  final val Extend = model.conv.toScala[IRTExtend[_0Arg, _0Arg]]
}
