package com.github.pshirshov.izumi.idealingua.translator.toscala.types


import com.github.pshirshov.izumi.idealingua.model.common.TypeId.{AdtId, DTOId}
import com.github.pshirshov.izumi.idealingua.model.common.{IndefiniteId, TypeId, TypeName, TypePath}
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.Service
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.Service.DefMethod.{Output, RPCMethod}
import com.github.pshirshov.izumi.idealingua.translator.toscala.{ClassSource, STContext}

import scala.meta._

final case class ServiceContext(ctx: STContext, svc: Service) {
  val typeName: TypeName = svc.id.name

  val basePath = TypePath(svc.id.domain, Seq(typeName))
  val svcPath = TypePath(svc.id.domain, Seq(s"${typeName}Server"))
  private val baseId: IndefiniteId = IndefiniteId(svc.id.domain.toPackage, s"$typeName")
  private val svcId: IndefiniteId = IndefiniteId(svc.id.domain.toPackage, name = s"${typeName}Server")

  val wrappedId: IndefiniteId = svcId.copy(name = s"${typeName}Wrapped")
  val clientId: IndefiniteId = svcId.copy(name = s"${typeName}Client")

  val svcBaseTpe: ScalaType = ctx.conv.toScala(baseId)
  val svcTpe: ScalaType = ctx.conv.toScala(svcId)
  val svcClientTpe: ScalaType = ctx.conv.toScala(clientId)
  val svcWrappedTpe: ScalaType = ctx.conv.toScala(wrappedId)

  import ctx.conv._
  val serviceInputBase: ScalaType = svcBaseTpe.within(s"In${typeName.capitalize}")
  val serviceOutputBase: ScalaType = svcBaseTpe.within(s"Out${typeName.capitalize}")
}

final case class FullServiceContext(service: ServiceContext, methods: List[ServiceMethodProduct])
final case class StructContext(source: ClassSource, struct: ScalaStruct)

final case class ServiceMethodProduct(ctx: STContext, sp: ServiceContext, method: RPCMethod) {

  import ctx.conv._

  def methodArity: Int = fields.size
  def nameTerm = Term.Name(name)
  
  def defnServer: Stat = {
    q"def $nameTerm(ctx: C, ..$signature): Result[${outputType.typeFull}]"
  }

  def defnClient: Stat = {
    q"def $nameTerm(..$signature): Result[${outputType.typeFull}]"
  }

  def defnWrapped: Stat = {
    q"def $nameTerm(ctx: C, ..$wrappedSignature): Result[${outputTypeWrapped.typeFull}]"
  }

  def bodyServer: Stat = {
    val sig = fields.map(f => q"input.${f.name}")

    val output = method.signature.output match {
      case _: Output.Singular =>
        q"_ServiceResult.map(result)(${outputTypeWrapped.termFull}.apply)"

      case _ =>
        q"result"
    }

    q"""def $nameTerm(ctx: C, ..$wrappedSignature): Result[${outputTypeWrapped.typeFull}] = {
             assert(input != null)
             val result = service.$nameTerm(ctx, ..$sig)
             $output
       }
     """
  }

  def dispatching: Case = {
    p" case IRTInContext(v: ${inputTypeWrapped.typeFull}, c) => _ServiceResult.map(${Term.Name(method.name)}(c, v))(v => v) // upcast "
  }

  def bodyClient: Stat = {
    val sig = fields.map(_.name)

    val output = method.signature.output match {
      case _: Output.Singular =>
        q"o.value"

      case _ =>
        q"o"
    }

    q"""def $nameTerm(..$signature): Result[${outputType.typeFull}] = {
             val packed = ${inputTypeWrapped.termFull}(..$sig)
             val dispatched = dispatcher.dispatch(packed)
             _ServiceResult.map(dispatched) {
               case o: ${outputTypeWrapped.typeFull} =>
                 $output
               case o =>
                 val id: String = ${Lit.String(s"${sp.typeName}.$name")}
                 throw new IRTTypeMismatchException(s"Unexpected input in $$id: $$o", o)
             }
       }
     """
  }


  def methodId: Term.Apply = q"IRTMethodId(${Lit.String(method.name)})"
  def fullMethodId = q"IRTMethod(`serviceId`, $methodId)"
  def fullMethodIdName = Term.Name(s"Met${method.name.capitalize}")

  def inputMatcher: Case =  {
    p" case _: ${inputTypeWrapped.typeFull} => $fullMethodIdName"
  }

  def outputMatcher: Case =  {
    p" case _: ${outputTypeWrapped.typeFull} => $fullMethodIdName"
  }

  protected def outputType: ScalaType = {
    ctx.conv.toScala(outputId)
  }

  def outputTypeWrapped: ScalaType = {
    ctx.conv.toScala(outputIdWrapped)
  }

  def outputIdWrapped: TypeId = {
    method.signature.output match {
      case _: Output.Singular =>
        DTOId(sp.basePath, s"${name.capitalize}Output")

      case _ =>
        outputId
    }
  }

  def inputTypeWrapped: ScalaType = sp.svcBaseTpe.within(inputIdWrapped.name)

  protected def outputId: TypeId = {
    method.signature.output match {
      case o: Output.Singular =>
        o.typeId

      case _: Output.Struct =>
        DTOId(sp.basePath, s"${name.capitalize}Output")

      case _: Output.Algebraic =>
        AdtId(sp.basePath, s"${name.capitalize}Output")
    }
  }

  def inputIdWrapped: DTOId = DTOId(sp.basePath, s"${name.capitalize}Input")

  protected def signature: List[Term.Param] = fields.toParams

  protected def wrappedSignature: List[Term.Param] = List(param"input: ${inputTypeWrapped.typeFull}")

  protected def inputStruct: CompositeStructure = ctx.tools.mkStructure(inputIdWrapped)

  protected def fields: List[ScalaField] = inputStruct.fields.all

  protected def name: String = method.name
}
