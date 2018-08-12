package com.github.pshirshov.izumi.idealingua.translator.toscala.types


import com.github.pshirshov.izumi.idealingua.model.common.TypeId.{AdtId, DTOId}
import com.github.pshirshov.izumi.idealingua.model.common.{IndefiniteId, TypeId, TypeName, TypePath}
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.Service
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.DefMethod.{Output, RPCMethod}
import com.github.pshirshov.izumi.idealingua.translator.toscala.STContext

import scala.meta._




final case class ServiceMethodProduct(ctx: STContext, sp: ServiceContext, method: RPCMethod) {

  import ctx.conv._

  def defnServerWrapped: Stat = {
    q"""object $nameTerm {
       }
     """
  }

  def defnMethod: Stat = {
    q"""object $nameTerm extends ${ctx.rt.IRTMethodSignature.init()} {
         final val id: ${ctx.rt.IRTMethodId.typeName} = ${ctx.rt.IRTMethodId.termName}(serviceId, ${ctx.rt.IRTMethodName.termName}(${Lit.String(name)}))

       }
     """
  }

  def defnCodec: Stat = {
    q"""object $nameTerm {
       }
     """
  }

  def defnClientWrapped: Stat = {
    val result: Type.Name = Type.Name("Unit")
    q"def $nameTerm(..${Input.signature}): $result"
  }

  def defnServer: Stat = {
    val result: Type.Name = Type.Name("Unit")
    q"def $nameTerm(ctx: ${sp.Ctx.t}, ..${Input.signature}): $result"
  }

  def defnClient: Stat = {
    val result: Type.Name = Type.Name("Unit")
    q"def $nameTerm(..${Input.signature}): $result"
  }


  protected def name: String = method.name

  protected object Input {
    def inputIdWrapped: DTOId = DTOId(sp.basePath, s"${name.capitalize}Input")

    def inputTypeWrapped: ScalaType = sp.svcBaseTpe.within(inputIdWrapped.name)

    def wrappedSignature: List[Term.Param] = List(param"input: ${inputTypeWrapped.typeFull}")

    def inputStruct: CompositeStructure = ctx.tools.mkStructure(inputIdWrapped)

    def fields: List[ScalaField] = inputStruct.fields.all

    def signature: List[Term.Param] = fields.toParams

    def methodArity: Int = fields.size
  }


  protected def nameTerm = Term.Name(name)

  //  protected def outputType: ScalaType = {
  //    ctx.conv.toScala(outputId)
  //  }
  //
  //  protected def outputTypeWrapped: ScalaType = {
  //    ctx.conv.toScala(outputIdWrapped)
  //  }
  //
  //  protected def outputIdWrapped: TypeId = {
  //    method.signature.output match {
  //      case _: Output.Singular =>
  //        DTOId(sp.basePath, s"${name.capitalize}Output")
  //
  //      case _ =>
  //        outputId
  //    }
  //  }
  //
  //
  //  protected def outputId: TypeId = {
  //    method.signature.output match {
  //      case o: Output.Singular =>
  //        o.typeId
  //
  //      case _: Output.Struct | _: Output.Void =>
  //        DTOId(sp.basePath, s"${name.capitalize}Output")
  //
  //      case _: Output.Algebraic =>
  //        AdtId(sp.basePath, s"${name.capitalize}Output")
  //    }
  //  }


  //  val result = ctx.conv.toScala(IndefiniteId(Seq.empty, "Result"))

  //  protected def result = sp.result.parameterize(List(outputType.typeFull)).typeFull
  //  protected def resultWrapped = sp.result.parameterize(List(outputTypeWrapped.typeFull)).typeFull


  //  def defnWrapped: Stat = {
  //    q"def $nameTerm(ctx: C, ..$wrappedSignature): $resultWrapped"
  //  }

  //  def bodyServer: Stat = {
  //    val sig = fields.map(f => q"input.${f.name}")
  //
  //    val output = method.signature.output match {
  //      case _: Output.Singular =>
  //        q"_ServiceResult.map(result)(${outputTypeWrapped.termFull}.apply)"
  //
  //      case _ =>
  //        q"result"
  //    }
  //
  //    q"""def $nameTerm(ctx: C, ..$wrappedSignature): $resultWrapped = {
  //             assert(input != null)
  //             val result = service.$nameTerm(ctx, ..$sig)
  //             $output
  //       }
  //     """
  //  }
  //
  //  def dispatching: Case = {
  //    p" case IRTInContext(v: ${inputTypeWrapped.typeFull}, c) => _ServiceResult.map(${Term.Name(method.name)}(c, v))(v => v) // upcast "
  //  }
  //
  //  def bodyClient: Stat = {
  //    val sig = fields.map(_.name)
  //
  //    val output = method.signature.output match {
  //      case _: Output.Singular =>
  //        q"o.value"
  //
  //      case _ =>
  //        q"o"
  //    }
  //
  //    q"""def $nameTerm(..$signature): $result = {
  //             val packed = ${inputTypeWrapped.termFull}(..$sig)
  //             val dispatched = dispatcher.dispatch(packed)
  //             _ServiceResult.map(dispatched) {
  //               case o: ${outputTypeWrapped.typeFull} =>
  //                 $output
  //               case o =>
  //                 val id: String = ${Lit.String(s"${sp.typeName}.$name")}
  //                 throw new IRTTypeMismatchException(s"Unexpected input in $$id: $$o", o)
  //             }
  //       }
  //     """
  //  }
  //
  //
  //  def methodId: Term.Apply = q"IRTMethodId(${Lit.String(method.name)})"
  //
  //  def fullMethodId = q"IRTMethod(`serviceId`, $methodId)"
  //
  //  def fullMethodIdName = Term.Name(s"Met${method.name.capitalize}")
  //
  //  def inputMatcher: Case = {
  //    p" case _: ${inputTypeWrapped.typeFull} => $fullMethodIdName"
  //  }
  //
  //  def outputMatcher: Case = {
  //    p" case _: ${outputTypeWrapped.typeFull} => $fullMethodIdName"
  //  }
  //


}
