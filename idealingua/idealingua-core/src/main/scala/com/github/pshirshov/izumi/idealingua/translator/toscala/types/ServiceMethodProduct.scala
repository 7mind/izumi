package com.github.pshirshov.izumi.idealingua.translator.toscala.types


import com.github.pshirshov.izumi.idealingua.model.common.TypeId.{AdtId, DTOId}
import com.github.pshirshov.izumi.idealingua.model.common.{ExtendedField, IndefiniteId, TypeId, TypeName}
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.Service
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.Service.DefMethod.{Output, RPCMethod}
import com.github.pshirshov.izumi.idealingua.model.typespace.structures.PlainStruct
import com.github.pshirshov.izumi.idealingua.translator.toscala.STContext

import scala.meta._

case class ServiceProduct(ctx: STContext, svc: Service) {
  private val typeName: TypeName = svc.id.name

  val svcId: IndefiniteId = IndefiniteId(svc.id).copy(name = s"${typeName}Server")
  val wrappedId: IndefiniteId = svcId.copy(name = s"${typeName}Wrapped")
  val clientId: IndefiniteId = svcId.copy(name = s"${typeName}Client")

  val svcTpe: ScalaType = ctx.conv.toScala(svcId)
  val svcClientTpe: ScalaType = ctx.conv(clientId)
  val svcWrappedTpe: ScalaType = ctx.conv(wrappedId)

}

case class ServiceMethodProduct(ctx: STContext, sp: ServiceProduct, method: RPCMethod) {

  import ctx.conv._

  def defnServer: Stat = {
    q"def ${Term.Name(name)}(ctx: C, ..$signature): Result[${outputType.typeFull}]"
  }

  def defnClient: Stat = {
    q"def ${Term.Name(name)}(..$signature): Result[${outputType.typeFull}]"
  }

  def defnWrapped: Stat = {
    q"def ${Term.Name(name)}(ctx: C, ..$wrappedSignature): Result[${outputType.typeFull}]"
  }

  def types: List[Defn] = {
    List(
      Some(inputDefn)
      , outputDefn
    )
      .flatMap(_.toList)
  }

  protected def outputDefn: Option[Defn] = {
    method.signature.output match {
      case o: Output.Singular =>
        None

      case o: Output.Struct =>
        Some(???)

      case o: Output.Algebraic =>
        Some(???)
    }
  }

  protected def inputDefn: Defn = {
    ???
  }

  protected def outputType: ScalaType = {
    ctx.conv.toScala(outputId)
  }

  protected def inputWrappedType: ScalaType = sp.svcWrappedTpe.within(inputWrappedId.name)

  protected def outputId: TypeId = {
    method.signature.output match {
      case o: Output.Singular =>
        o.typeId

      case o: Output.Struct =>
        DTOId(sp.svcId, s"${name.capitalize}Output")

      case o: Output.Algebraic =>
        AdtId(sp.svcId, s"${name.capitalize}Output")
    }
  }

  protected def inputWrappedId: DTOId = DTOId(sp.svcId, s"${name.capitalize}Input")

  protected def signature: List[Term.Param] = fields.toParams

  protected def wrappedSignature: List[Term.Param] = List(param"input: ${inputWrappedType.typeFull}")

  protected def inputStruct: CompositeStructure = ctx.tools.mkStructure(inputWrappedId)

  protected def fields: List[ScalaField] = inputStruct.fields.all

  protected def name: String = method.name

  //  def defn: Stat = {
  //    q"def ${Term.Name(name)}(input: $input): Result[$output]"
  //  }
  //
  //  def defnDispatch: Stat = {
  //    q"def ${Term.Name(name)}(input: $input): Result[$output] = dispatcher.dispatch(input, classOf[$output])"
  //  }
  //
  //  def defnExplode: Stat = {
  //    val code = in.explodedSignature.map(p => q"${Term.Name(p.name.value)} = input.${Term.Name(p.name.value)}")
  //    q"def ${Term.Name(name)}(input: $input): Result[$output] = service.${Term.Name(name)}(..$code)"
  //  }
  //
  //  def defnExploded: Stat = {
  //    q"def ${Term.Name(name)}(..${in.explodedSignature}): Result[$output]"
  //  }
  //
  //
  //  def defnCompress: Stat = {
  //    val code = in.explodedSignature.map(p => q"${Term.Name(p.name.value)} = ${Term.Name(p.name.value)}")
  //
  //    q"""def ${Term.Name(name)}(..${in.explodedSignature}): Result[$output] = {
  //       service.${Term.Name(name)}(${in.t.termFull}(..$code))
  //      }
  //      """
  //  }
  //
  //  def routingClause: Case = {
  //    Case(
  //      Pat.Typed(Pat.Var(Term.Name("value")), input)
  //      , None
  //      , q"service.${Term.Name(name)}(value)"
  //    )
  //  }
}
