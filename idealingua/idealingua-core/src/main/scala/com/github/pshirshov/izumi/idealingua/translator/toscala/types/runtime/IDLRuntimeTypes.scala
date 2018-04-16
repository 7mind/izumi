package com.github.pshirshov.izumi.idealingua.translator.toscala.types.runtime

import com.github.pshirshov.izumi.idealingua.model.common.TypeId
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.{DomainDefinition, DomainId, TypeDef}
import com.github.pshirshov.izumi.idealingua.runtime.model._
import com.github.pshirshov.izumi.idealingua.runtime.model.introspection.{IDLDomainCompanion, IDLTypeInfo, WithInfo}
import com.github.pshirshov.izumi.idealingua.runtime.services.{ServiceResult, WithResult, WithResultType}
import com.github.pshirshov.izumi.idealingua.runtime.transport.{AbstractClientDispatcher, AbstractServerDispatcher}
import com.github.pshirshov.izumi.idealingua.translator.toscala.types.ScalaTypeConverter

import scala.reflect._

class Pkg private (pkgParts: Seq[String]) {
  final val pkg = pkgParts.mkString(".")
  final val conv = new ScalaTypeConverter(DomainId(pkgParts.init, pkgParts.last))
}

object Pkg {
  def parentOf[T : ClassTag]: Pkg = {
     val classPkg = classTag[T].runtimeClass.getPackage.getName
    val classPkgParts = classPkg.split('.').toSeq
    new Pkg(classPkgParts)
  }

  def of[T : ClassTag]: Pkg = {
    val classPkg = classTag[T].runtimeClass.getPackage.getName
    val classPkgParts = classPkg.split('.')
    new Pkg(classPkgParts)
  }

}

object IDLRuntimeTypes {

  val model: Pkg = Pkg.parentOf[TypeId]
  val transport: Pkg = Pkg.parentOf[AbstractClientDispatcher[_1Arg, _]]
  val services: Pkg = Pkg.of[ServiceResult[_1Arg]]

//  private final val transportPkg = classOf[].getPackage.getName
//  private final val runtimePkgParts = transportPkg.split('.').toSeq.init
//  final val runtimePkg = runtimePkgParts.mkString(".")
//  final val runtimeConv = new ScalaTypeConverter(DomainId(runtimePkgParts.init, runtimePkgParts.last))


  private type _1Arg[R] = R

  final val generated = model.conv.toScala[IDLGeneratedType]

  final val enum = model.conv.toScala[IDLEnum]
  final val enumEl = model.conv.toScala[IDLEnumElement]

  final val tIDLIdentifier = model.conv.toScala[IDLIdentifier]

  final val adt = model.conv.toScala[IDLAdt]
  final val adtEl = model.conv.toScala[IDLAdtElement]

  final val WithResult = services.conv.toScala[WithResult[_1Arg]]
  final val WithResultType = services.conv.toScala[WithResultType[_1Arg]]

  final val idtService = model.conv.toScala[IDLService[_1Arg]]
  final val serverDispatcher = transport.conv.toScala[AbstractServerDispatcher[_1Arg, _]]
  final val clientDispatcher = transport.conv.toScala[AbstractClientDispatcher[_1Arg, _]]
  final val clientWrapper = transport.conv.toScala[IDLClientWrapper[_1Arg, _]]
  final val serverWrapper = transport.conv.toScala[IDLServiceExploded[_1Arg, _]]
  final val input = model.conv.toScala[IDLInput]
  final val output = model.conv.toScala[IDLOutput]

  // introspection
  final val typeId = model.conv.toScala[TypeId]
  final val typeInfo = model.conv.toScala[IDLTypeInfo]
  final val withTypeInfo = model.conv.toScala[WithInfo]
  final val tFinalDefinition = model.conv.toScala[TypeDef]
  final val tDomainDefinition = model.conv.toScala[DomainDefinition]
  final val tDomainId = model.conv.toScala[DomainId]
  final val tDomainCompanion = model.conv.toScala[IDLDomainCompanion]

}
