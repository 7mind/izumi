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

    val invoke = method.signature.output match {
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
    val exception =
      q"""
          val id = ${Lit.String(s"${sp.typeName}.${sp.svcWrappedClientTpe.termName.value}.$nameTerm")}
          val expected = classOf[_M.$nameTerm.Input].toString
          ${sp.BIO.n}.terminate(new IRTTypeMismatchException(s"Unexpected type in $$id: $$v, expected $$expected got $${v.getClass}", v, None))
       """

    method.signature.output match {
      case DefMethod.Output.Singular(_) =>
        q"""def $nameTerm(..${Input.signature}): ${Output.outputType} = {
               _dispatcher
                 .dispatch(IRTMuxRequest(IRTReqBody(new _M.$nameTerm.Input(..${Input.sigDirectCall})), _M.$nameTerm.id))
                 .redeem(
                  { err => ${sp.BIO.n}.terminate(err) },
                  {
                    case IRTMuxResponse(IRTResBody(v: _M.$nameTerm.Output), method) if method == _M.$nameTerm.id =>
                      ${sp.BIO.n}.point(v.value)
                    case v => $exception
                  })
           }"""

      case DefMethod.Output.Void() =>
        q"""def $nameTerm(..${Input.signature}): ${Output.outputType} = {
               _dispatcher
                 .dispatch(IRTMuxRequest(IRTReqBody(new _M.$nameTerm.Input(..${Input.sigDirectCall})), _M.$nameTerm.id))
                 .redeem(
                   { err => ${sp.BIO.n}.terminate(err) },
                   {
                     case IRTMuxResponse(IRTResBody(_: _M.$nameTerm.Output), method) if method == _M.$nameTerm.id =>
                       ${sp.BIO.n}.point(())
                     case v => $exception
            })
           }"""

      case DefMethod.Output.Algebraic(_) | DefMethod.Output.Struct(_) =>
        q"""def $nameTerm(..${Input.signature}): ${Output.outputType} = {
               _dispatcher
                 .dispatch(IRTMuxRequest(IRTReqBody(new _M.$nameTerm.Input(..${Input.sigDirectCall})), _M.$nameTerm.id))
                 .redeem(
                    { err => ${sp.BIO.n}.terminate(err) },
                    {
                      case IRTMuxResponse(IRTResBody(v: _M.$nameTerm.Output), method) if method == _M.$nameTerm.id =>
                        ${sp.BIO.n}.point(v)
                      case v => $exception
                    })
           }"""
    }
  }

  def defnServer: Stat = {
    q"def $nameTerm(ctx: ${sp.Ctx.t}, ..${Input.signature}): ${Output.outputType}"
  }

  def defnClient: Stat = {
    q"def $nameTerm(..${Input.signature}): ${Output.outputType}"
  }


  protected def name: String = method.name

  protected def nameTerm = Term.Name(name)

  protected object Input {
    def wrappedSignature: List[Term.Param] = List(param"input: ${scalaType.typeFull}")

    def inputDefn: List[Defn] = ctx.compositeRenderer.defns(inputStruct, ClassSource.CsMethodInput(sp, ServiceMethodProduct.this)).render

    def signature: List[Term.Param] = fields.toParams

    def sigCall: List[Term.Select] = fields.map(f => q"input.${f.name}")
    def sigDirectCall: List[Term.Name] = fields.map(_.name)


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
}
