package izumi.idealingua.translator.toscala.types


import izumi.idealingua.model.common.TypeId.{AdtId, DTOId}
import izumi.idealingua.model.il.ast.typed.DefMethod
import izumi.idealingua.model.il.ast.typed.DefMethod.RPCMethod
import izumi.idealingua.model.il.ast.typed.TypeDef.Adt
import izumi.idealingua.translator.toscala.STContext

import scala.collection.immutable
import scala.meta._


final case class ServiceMethodProduct(ctx: STContext, sp: ServiceContext, method: RPCMethod) {

  import ctx.conv._

  // TODO:TSASSYMMETRY this method is a workaround for assymetry between typespace representation and scala code we want to render
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

    q"""object $nameTerm extends IRTCirceMarshaller {
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
            ${sp.BIO.n}.map(_service.$nameTerm(ctx, ..${Input.sigCall}))(v => new Output(v))
           }"""

      case DefMethod.Output.Void() =>
        q"""def invoke(ctx: ${sp.Ctx.t}, input: Input): Just[Output] = {
              assert(ctx != null && input != null)
              ${sp.BIO.n}.map(_service.$nameTerm(ctx, ..${Input.sigCall}))(_ => new Output())
           }"""

      case DefMethod.Output.Algebraic(_) | DefMethod.Output.Struct(_) =>
        q"""def invoke(ctx: ${sp.Ctx.t}, input: Input): Just[Output] = {
              assert(ctx != null && input != null)
              _service.$nameTerm(ctx, ..${Input.sigCall})
           }"""

      case DefMethod.Output.Alternative(_, _) =>
        q"""
           def invoke(ctx: ${sp.Ctx.t}, input: Input): Just[Output] = {
              ${sp.BIO.n}.redeem(_service.$nameTerm(ctx, ..${Input.sigCall}))(
                      err => _F.pure(new ${Output.negativeBranchType.typeFull}(err))
                      , succ => _F.pure(new ${Output.positiveBranchType.typeFull}(succ))
                   )
           }"""
    }

    q"""object $nameTerm extends IRTMethodWrapper[${sp.F.t}, ${sp.Ctx.t}] {
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
               ${sp.BIO.n}.redeem(_dispatcher
                 .dispatch(IRTMuxRequest(IRTReqBody(new _M.$nameTerm.Input(..${Input.sigDirectCall})), _M.$nameTerm.id))
               )(
                  { err => ${sp.BIO.n}.terminate(err) },
                  {
                    case IRTMuxResponse(IRTResBody(v: _M.$nameTerm.Output), method) if method == _M.$nameTerm.id =>
                      ${sp.BIO.n}.pure(v.value)
                    case v => $exception
                  })
           }"""

      case DefMethod.Output.Void() =>
        q"""def $nameTerm(..${Input.signature}): ${Output.outputType} = {
               ${sp.BIO.n}.redeem(_dispatcher
                 .dispatch(IRTMuxRequest(IRTReqBody(new _M.$nameTerm.Input(..${Input.sigDirectCall})), _M.$nameTerm.id))
               )(
                   { err => ${sp.BIO.n}.terminate(err) },
                   {
                     case IRTMuxResponse(IRTResBody(_: _M.$nameTerm.Output), method) if method == _M.$nameTerm.id =>
                       ${sp.BIO.n}.pure(())
                     case v => $exception
            })
           }"""

      case DefMethod.Output.Algebraic(_) | DefMethod.Output.Struct(_) =>
        q"""def $nameTerm(..${Input.signature}): ${Output.outputType} = {
            ${sp.BIO.n}.redeem(_dispatcher
                 .dispatch(IRTMuxRequest(IRTReqBody(new _M.$nameTerm.Input(..${Input.sigDirectCall})), _M.$nameTerm.id))
               )(
                    { err => ${sp.BIO.n}.terminate(err) },
                    {
                      case IRTMuxResponse(IRTResBody(v: _M.$nameTerm.Output), method) if method == _M.$nameTerm.id =>
                        ${sp.BIO.n}.pure(v)
                      case v => $exception
                    })
           }"""

      case DefMethod.Output.Alternative(_, _) =>
        q"""def $nameTerm(..${Input.signature}): ${Output.outputType} = {
           ${sp.BIO.n}.redeem(_dispatcher
                 .dispatch(IRTMuxRequest(IRTReqBody(new _M.$nameTerm.Input(..${Input.sigDirectCall})), _M.$nameTerm.id))
               )(
                    { err => ${sp.BIO.n}.terminate(err) },
                    {
                       case IRTMuxResponse(IRTResBody(r), method) if method == _M.$nameTerm.id =>
                         r match {
                           case va : ${Output.negativeBranchType.typeFull} =>
                             ${sp.BIO.n}.fail(va.value)

                           case va : ${Output.positiveBranchType.typeFull} =>
                             ${sp.BIO.n}.pure(va.value)

                           case v =>
                             $exception
                         }
                       case v =>
                         $exception
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

    // TODO:TSASSYMMETRY this method is a workaround for assymetry between typespace representation and scala code we want to render
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
      q"""def decodeRequest[Or[+_, +_] : IRTBIO]: PartialFunction[IRTJsonBody, Or[IRTDecodingFailure, IRTReqBody]] = {
            case IRTJsonBody(m, packet) if m == id => this.decoded[Or, IRTReqBody](packet.as[Input].map(v => IRTReqBody(v)))
          }
       """
  }

  protected object Output {

    private def typename: String = ctx.typespace.tools.methodToOutputName(method)



    def outputType: Type = method.signature.output match {
      case DefMethod.Output.Void() =>
        t"Just[Unit]"
      case DefMethod.Output.Singular(t) =>
        val scalaType = ctx.conv.toScala(t)
        t"Just[${scalaType.typeFull}]"
      case DefMethod.Output.Struct(_) | DefMethod.Output.Algebraic(_) =>
        val aliasType: ScalaType = sp.svcMethods.within(name).within("Output")
        t"Just[${aliasType.typeFull}]"
      case DefMethod.Output.Alternative(s, f) =>
        t"Or[${render_Id_SHIM(f, negativeType.typeFull)}, ${render_Id_SHIM(s, positiveType.typeFull)}]"
    }

    private def positiveId: String = ctx.typespace.tools.methodToPositiveTypeName(method)
    private def negativeId: String = ctx.typespace.tools.methodToNegativeTypeName(method)

    def positiveType: ScalaType =  sp.svcMethods.within(positiveId)

    def negativeType: ScalaType = sp.svcMethods.within(negativeId)

    private def adtId = AdtId(sp.basePath, typename)

    private def dtoId = DTOId(sp.basePath, typename)

    def positiveBranchType: ScalaType = wrappedTypespaceType.within(ctx.typespace.tools.toPositiveBranchName(adtId))

    def negativeBranchType: ScalaType = wrappedTypespaceType.within(ctx.typespace.tools.toNegativeBranchName(adtId))

    // TODO:TSASSYMMETRY this method is a workaround for assymetry between typespace representation and scala code we want to render
    def wrappedTypespaceType: ScalaType = {
      val id = method.signature.output match {
        case DefMethod.Output.Struct(_) | DefMethod.Output.Void() | DefMethod.Output.Singular(_) =>
          dtoId
        case DefMethod.Output.Algebraic(_) | DefMethod.Output.Alternative(_, _) =>
          adtId
      }
      ctx.conv.toScala(id)
    }

    def outputDefn: List[Defn] = {
      renderOutput(typename, method.signature.output)
    }

    def defnEncoder: Defn.Def = {
      q"""def encodeResponse: PartialFunction[IRTResBody, IRTJson] = {
            case IRTResBody(value: Output) => value.asJson
          }"""
    }

    def defnDecoder: Defn.Def = {
      q"""def decodeResponse[Or[+_, +_] : IRTBIO]: PartialFunction[IRTJsonBody, Or[IRTDecodingFailure, IRTResBody]] = {
            case IRTJsonBody(m, packet) if m == id =>
              decoded[Or, IRTResBody](packet.as[Output].map(v => IRTResBody(v)))
          }"""
    }

    private def renderOutput(typename: String, out: DefMethod.Output): List[Defn] = {
      out match {
        case DefMethod.Output.Struct(_) | DefMethod.Output.Void() | DefMethod.Output.Singular(_) =>
          val typespaceId: DTOId = DTOId(sp.basePath, typename)
          val struct = ctx.tools.mkStructure(typespaceId)
          ctx.compositeRenderer.defns(struct, ClassSource.CsMethodOutput(sp, ServiceMethodProduct.this)).render

        case DefMethod.Output.Algebraic(_) =>
          val typespaceId: AdtId = AdtId(sp.basePath, typename)
          ctx.adtRenderer.renderAdt(ctx.typespace.apply(typespaceId).asInstanceOf[Adt], List.empty).render

        case DefMethod.Output.Alternative(success, failure) =>
          ctx.adtRenderer.renderAdt(ctx.typespace.apply(adtId).asInstanceOf[Adt], List.empty).render ++
            render_SHIM(positiveId, success) ++
            render_SHIM(negativeId, failure)
      }
    }

    private def render_SHIM(typename: String, out: DefMethod.Output): immutable.Seq[Defn] = {
      out match {
        case _: DefMethod.Output.Singular =>
          List.empty
        case o =>
          renderOutput(typename, o)

      }
    }

    def render_Id_SHIM(s: DefMethod.Output.NonAlternativeOutput, typeFull: Type): Type = {
      s match {
        case o: DefMethod.Output.Singular =>
          ctx.conv.toScala(o.typeId).typeFull
        case _ => typeFull
      }
    }
//    private def render_Id_SHIM(typename: String, out: DefMethod.Output) = {
//      out match {
//        case o: DefMethod.Output.Singular =>
//          List.empty
//        case o =>
//          renderOutput(positiveId, o)
//
//      }
//    }
//
  }


}
