package com.github.pshirshov.izumi.idealingua.translator.toscala.types


import com.github.pshirshov.izumi.idealingua.model.common.TypeId.{AdtId, DTOId}
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.DefMethod
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.DefMethod.RPCMethod
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.TypeDef.Adt
import com.github.pshirshov.izumi.idealingua.translator.toscala.STContext

import scala.meta._


final case class ServiceMethodProduct(ctx: STContext, sp: ServiceContext, method: RPCMethod) {

  import ctx.conv._

  @deprecated("", "")
  def defStructs: List[Stat] = {
    Input.inputDefn ++ Output.outputDefn
  }

  def defnMethod: Stat = {
    q"""object $nameTerm extends ${ctx.rt.IRTMethodSignature.init()} {
         final val id: ${ctx.rt.IRTMethodId.typeName} = ${ctx.rt.IRTMethodId.termName}(serviceId, ${ctx.rt.IRTMethodName.termName}(${Lit.String(name)}))
         type Input = ${Input.typespaceType.typeName}
         type Output = ${Output.wrappedTypespaceType.typeName}
       }
     """
  }

  def defnMethodRegistration: Term = nameTerm

  def defnCodecRegistration: Term.ApplyInfix = {
    q""" ${sp.svcMethods.termName}.$nameTerm.id -> ${sp.svcCodecs.termName}.$nameTerm """
  }

  def defnCodec: Stat = {
    val methods = List(Input.defnEncoder, Input.defnDecoder, Output.defnEncoder, Output.defnDecoder)

    q"""object $nameTerm extends IRTCirceMarshaller[${sp.BIO.t}] with ${ctx.rt.WithResultZio.init()} {
          import ${sp.svcMethods.termName}.$nameTerm._
          ..$methods
       }
     """
  }

  def defnServerWrapped: Stat = {

    def invoke: Defn.Def = method.signature.output match {
      case DefMethod.Output.Singular(_) =>
        q"""def invoke(ctx: ${sp.Ctx.t}, input: Input): Just[Output] = {
              assert(ctx != null && input != null)
              _service.$nameTerm(ctx, ..${Input.sigCall})
                .map(v => new Output(v))
           }"""

      case DefMethod.Output.Void() =>
        q"""def invoke(ctx: ${sp.Ctx.t}, input: Input): Just[Output] = {
              assert(ctx != null && input != null)
              _service.$nameTerm(ctx, ..${Input.sigCall})
                .map(_ => new Output())
           }"""

      case DefMethod.Output.Algebraic(_) | DefMethod.Output.Struct(_) =>
        q"""def invoke(ctx: ${sp.Ctx.t}, input: Input): Just[Output] = {
              assert(ctx != null && input != null)
              _service.$nameTerm(ctx, ..${Input.sigCall})
           }"""
    }

    q"""object $nameTerm extends IRTMethodWrapper[${sp.BIO.t}, ${sp.Ctx.t}] with ${ctx.rt.WithResultZio.init()} {
          import ${sp.svcMethods.termName}.$nameTerm._

          val signature: ${sp.svcMethods.termName}.$nameTerm.type = ${sp.svcMethods.termName}.$nameTerm
          val marshaller: ${sp.svcCodecs.termName}.$nameTerm.type = ${sp.svcCodecs.termName}.$nameTerm

          $invoke
       }
     """
  }

  def defnClientWrapped: Stat = {
    q"def $nameTerm(..${Input.signature}): ${Output.outputType} = ???"
  }

  def defnServer: Stat = {
    val result: Type.Name = Type.Name("Unit")
    q"def $nameTerm(ctx: ${sp.Ctx.t}, ..${Input.signature}): ${Output.outputType}"
  }

  def defnClient: Stat = {
    val result: Type.Name = Type.Name("Unit")
    q"def $nameTerm(..${Input.signature}): ${Output.outputType}"
  }


  protected def name: String = method.name

  protected def nameTerm = Term.Name(name)

  protected object Input {
    def wrappedSignature: List[Term.Param] = List(param"input: ${scalaType.typeFull}")

    def inputDefn: List[Defn] = ctx.compositeRenderer.defns(inputStruct, ClassSource.CsMethodInput(sp, ServiceMethodProduct.this)).render

    def signature: List[Term.Param] = fields.toParams

    def sigCall: List[Term.Select] = fields.map(f => q"input.${f.name}")


    @deprecated("", "")
    def typespaceType: ScalaType = ctx.conv.toScala(typespaceId)

    private def typespaceId: DTOId = DTOId(sp.basePath, s"${name.capitalize}Input")

    private def scalaType: ScalaType = sp.svcMethods.within(name).within("Input")

    private def inputStruct: CompositeStructure = ctx.tools.mkStructure(typespaceId)

    private def fields: List[ScalaField] = inputStruct.fields.all

    def defnEncoder: Defn.Def =
      q"""def encodeRequest: PartialFunction[IRTReqBody, IRTJson] = {
            case IRTReqBody(value: Input) => value.asJson
          }"""

    def defnDecoder: Defn.Def =
      q"""def decodeRequest: PartialFunction[IRTJsonBody, Just[IRTReqBody]] = {
            case IRTJsonBody(m, packet) if m == id => this.decoded(packet.as[Input].map(v => IRTReqBody(v)))
          }
       """
  }

  protected object Output {

    private def scalaType: ScalaType = sp.svcMethods.within(name).within("Output")

    def wrappedOutputType: Type = t"Just[${scalaType.typeFull}]"

    def outputType: Type = method.signature.output match {
      case DefMethod.Output.Void() =>
        t"Just[Unit]"
      case DefMethod.Output.Singular(t) =>
        t"Just[${ctx.conv.toScala(t).typeFull}]"
      case DefMethod.Output.Struct(_) | DefMethod.Output.Algebraic(_) =>
        wrappedOutputType
    }

    @deprecated("", "")
    def wrappedTypespaceType: ScalaType = {
      val id = method.signature.output match {
        case DefMethod.Output.Struct(_) | DefMethod.Output.Void() | DefMethod.Output.Singular(_) =>
          DTOId(sp.basePath, s"${name.capitalize}Output")

        case DefMethod.Output.Algebraic(_) =>
          AdtId(sp.basePath, s"${name.capitalize}Output")
      }
      ctx.conv.toScala(id)
    }

    def outputDefn: List[Defn] = method.signature.output match {
      case DefMethod.Output.Struct(_) | DefMethod.Output.Void() | DefMethod.Output.Singular(_) =>
        val typespaceId: DTOId = DTOId(sp.basePath, s"${name.capitalize}Output")
        val struct = ctx.tools.mkStructure(typespaceId)
        ctx.compositeRenderer.defns(struct, ClassSource.CsMethodInput(sp, ServiceMethodProduct.this)).render


      case DefMethod.Output.Algebraic(_) =>
        val typespaceId: AdtId = AdtId(sp.basePath, s"${name.capitalize}Output")
        ctx.adtRenderer.renderAdt(ctx.typespace.apply(typespaceId).asInstanceOf[Adt], List.empty).render
    }

    def defnEncoder: Defn.Def = {
      method.signature.output match {
        case _ =>
          q"""def encodeResponse: PartialFunction[IRTResBody, IRTJson] = {
                case IRTResBody(value: Output) => value.asJson
              }"""
      }
    }

    def defnDecoder: Defn.Def =
      method.signature.output match {
        case _ =>
          q"""def decodeResponse: PartialFunction[IRTJsonBody, Just[IRTResBody]] = {
                case IRTJsonBody(m, packet) if m == id =>
                  decoded(packet.as[Output].map(v => IRTResBody(v)))
          }"""
      }

  }


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
