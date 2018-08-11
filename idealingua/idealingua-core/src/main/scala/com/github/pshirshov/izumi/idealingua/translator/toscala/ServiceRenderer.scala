package com.github.pshirshov.izumi.idealingua.translator.toscala

import com.github.pshirshov.izumi.idealingua.model.common.TypeId.{AdtId, DTOId}
import com.github.pshirshov.izumi.idealingua.model.exceptions.IDLException
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.DefMethod.RPCMethod
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.Service
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.TypeDef.Adt
import com.github.pshirshov.izumi.idealingua.translator.toscala.products.CogenProduct.CogenServiceProduct
import com.github.pshirshov.izumi.idealingua.translator.toscala.products.RenderableCogenProduct
import com.github.pshirshov.izumi.idealingua.translator.toscala.types._

import scala.meta._

class ServiceRenderer(ctx: STContext) {
  import ctx._

  def renderService(svc: Service): RenderableCogenProduct = {
    val sp = ServiceContext(ctx, svc)

    val decls = svc.methods
      .collect({ case c: RPCMethod => c })
      .map(ServiceMethodProduct(ctx, sp, _))

    val inputs = decls
      .map {
        dto =>
          val struct = ctx.tools.mkStructure(dto.inputIdWrapped)
          ctx.compositeRenderer.defns(struct, ClassSource.CsMethodInput(sp, dto))
      }
      .flatMap(_.render)

    val outputs = decls
      .map(d => (d.outputIdWrapped, d))
      .map {
        case (dto: DTOId, d) =>
          val struct = ctx.tools.mkStructure(dto)
          ctx.compositeRenderer.defns(struct, ClassSource.CsMethodOutput(sp, d))
        case (adt: AdtId, _) =>
          ctx.adtRenderer.renderAdt(typespace.apply(adt).asInstanceOf[Adt], List(sp.serviceOutputBase.init()))
        case o =>
          throw new IDLException(s"Impossible case/service output: $o")
      }
      .flatMap(_.render)

    val inputMappers = decls.map(_.inputMatcher)
    val outputMappers = decls.map(_.outputMatcher)
    val packing = decls.map(_.bodyClient)
    val unpacking = decls.map(_.bodyServer)
    val dispatchers = decls.map(_.dispatching)

    val methodIds = decls.map {
      decl =>
        q""" val ${Pat.Var(decl.fullMethodIdName)} = ${decl.fullMethodId} """
    }

    val zeroargCases: List[Case] = decls.filter(_.methodArity == 0).map({
      method =>
        p"""case ${method.fullMethodIdName}  =>
              Some(${method.inputTypeWrapped.termFull}())"""
    })

    val qqService =
      q"""trait ${sp.svcTpe.typeName}[R[_], C] extends ${rt.WithResultZio.parameterize("R").init()} {
            ..${decls.map(_.defnServer)}
          }"""

    val qqClient =
      q"""trait ${sp.svcClientTpe.typeName}[R[_]] extends ${rt.WithResultZio.parameterize("R").init()} {
            ..${decls.map(_.defnClient)}
          }"""

    val qqClientCompanion =
      q"""object ${sp.svcClientTpe.termName} {

         }"""

    val qqWrapped =
      q"""trait ${sp.svcWrappedTpe.typeName}[R[_], C] extends ${rt.WithResultZio.parameterize("R").init()} {
            ..${decls.map(_.defnWrapped)}
          }"""

    val qqPackingDispatcher =
      q"""
           trait PackingDispatcher[R[_]]
             extends ${sp.svcClientTpe.parameterize("R").init()}
               with ${rt.WithResult.parameterize("R").init()} {
             def dispatcher: IRTDispatcher[${sp.serviceInputBase.typeFull}, ${sp.serviceOutputBase.typeFull}, R]

               ..$packing
           }
       """
    val qqPackingDispatcherCompanion =
      q"""  object PackingDispatcher {
        class Impl[R[_] : IRTResult](val dispatcher: IRTDispatcher[${sp.serviceInputBase.typeFull}, ${sp.serviceOutputBase.typeFull}, R]) extends PackingDispatcher[R] {
          override protected def _ServiceResult: IRTResult[R] = implicitly
        }
      }"""

    val qqUnpackingDispatcher =
      q"""
  trait UnpackingDispatcher[R[_], C]
    extends ${sp.svcWrappedTpe.parameterize("R", "C").init()}
      with IRTGeneratedUnpackingDispatcher[C, R, ${sp.serviceInputBase.typeFull}, ${sp.serviceOutputBase.typeFull}] {
    def service: ${sp.svcTpe.typeFull}[R, C]

    def dispatch(input: IRTInContext[${sp.serviceInputBase.typeFull}, C]): ${sp.dispatcherResult} = {
      input match {
        ..case $dispatchers
      }
    }

    def identifier: IRTServiceId = serviceId

    protected def toMethodId(v: ${sp.serviceInputBase.typeFull}): IRTMethod = ${sp.svcWrappedTpe.termName}.toMethodId(v)

    protected def toMethodId(v: ${sp.serviceOutputBase.typeFull}): IRTMethod = ${sp.svcWrappedTpe.termName}.toMethodId(v)

    import scala.reflect._

    override protected def inputTag: ClassTag[${sp.serviceInputBase.typeFull}] = classTag

    override protected def outputTag: ClassTag[${sp.serviceOutputBase.typeFull}] = classTag

    protected def toZeroargBody(v: IRTMethod): Option[${sp.serviceInputBase.typeFull}] = {
      v match {
        ..case $zeroargCases

        case _ =>
          None
      }
    }

    ..$unpacking
  }"""

    val qqUnpackingDispatcherCompanion =
      q"""  object UnpackingDispatcher {
        class Impl[R[_] : IRTResult, C](val service: ${sp.svcTpe.typeFull}[R, C]) extends UnpackingDispatcher[R, C] {
          override protected def _ServiceResult: IRTResult[R] = implicitly
        }
      }"""

    val qqSafeToUnsafeBridge =
      q"""
        class SafeToUnsafeBridge[R[_] : IRTResult](dispatcher: IRTDispatcher[IRTMuxRequest[Product], IRTMuxResponse[Product], R])
           extends IRTDispatcher[${sp.serviceInputBase.typeFull}, ${sp.serviceOutputBase.typeFull}, R]
           with ${rt.WithResult.parameterize("R").init()} {
             override protected def _ServiceResult: IRTResult[R] = implicitly

             import IRTResult._

             override def dispatch(input: ${sp.serviceInputBase.typeFull}): ${sp.dispatcherResult} = {
               dispatcher.dispatch(IRTMuxRequest(input : Product, toMethodId(input))).map {
                 case IRTMuxResponse(t: ${sp.serviceOutputBase.typeFull}, _) =>
                   t
                 case o =>
                   val id: String = ${Lit.String(s"${sp.typeName}.SafeToUnsafeBridge.dispatch")}
                   throw new IRTTypeMismatchException(s"Unexpected output in $$id: $$o", o, None)
               }
             }
           }
       """

    val qqWrappedCompanion =
      q"""
         object ${sp.svcWrappedTpe.termName}
          extends IRTIdentifiableServiceDefinition
            with IRTWrappedServiceDefinition
            with IRTWrappedUnsafeServiceDefinition {

          type Input =  ${sp.serviceInputBase.typeFull}
          type Output = ${sp.serviceOutputBase.typeFull}


           override type ServiceServer[R[_], C] = ${sp.svcTpe.parameterize("R", "C").typeFull}
           override type ServiceClient[R[_]] = ${sp.svcClientTpe.parameterize("R").typeFull}

  def client[R[_] : IRTResult](dispatcher: IRTDispatcher[${sp.serviceInputBase.typeFull}, ${sp.serviceOutputBase.typeFull}, R]): ${sp.svcClientTpe.parameterize("R").typeFull} = {
    new PackingDispatcher.Impl[R](dispatcher)
  }


  def clientUnsafe[R[_] : IRTResult](dispatcher: IRTDispatcher[IRTMuxRequest[Product], IRTMuxResponse[Product], R]): ${sp.svcClientTpe.parameterize("R").typeFull} = {
    client(new SafeToUnsafeBridge[R](dispatcher))
  }

  def server[R[_] : IRTResult, C](service: ${sp.svcTpe.parameterize("R", "C").typeFull}): IRTDispatcher[IRTInContext[${sp.serviceInputBase.typeFull}, C], ${sp.serviceOutputBase.typeFull}, R] = {
    new UnpackingDispatcher.Impl[R, C](service)
  }


  def serverUnsafe[R[_] : IRTResult, C](service: ${sp.svcTpe.parameterize("R", "C").typeFull}): IRTUnsafeDispatcher[C, R] = {
    new UnpackingDispatcher.Impl[R, C](service)
  }

          val serviceId = IRTServiceId(${Lit.String(sp.typeName)})

          def toMethodId(v: ${sp.serviceInputBase.typeFull}): IRTMethod = {
            v match {
              ..case $inputMappers
            }
          }

          def toMethodId(v: ${sp.serviceOutputBase.typeFull}): IRTMethod = {
            v match {
              ..case $outputMappers
            }
          }

          $qqPackingDispatcher
          $qqPackingDispatcherCompanion
          $qqSafeToUnsafeBridge
          $qqUnpackingDispatcher
          $qqUnpackingDispatcherCompanion
          ..$methodIds
         }
       """

    val qqServiceCompanion =
      q"""
         object ${sp.svcTpe.termName} {
         }
       """

    import CogenServiceProduct._

    val input = Pair(q"sealed trait ${sp.serviceInputBase.typeName} extends Any with Product", q"object ${sp.serviceInputBase.termName} {}")
    val output = Pair(q"sealed trait ${sp.serviceOutputBase.typeName} extends Any with Product", q"object ${sp.serviceOutputBase.termName} {}")

    // TODO: move Any into extensions
    val qqBaseCompanion =
      q"""
         object ${sp.svcBaseTpe.termName} {

           ..$outputs
           ..$inputs
         }
       """


    val out = CogenServiceProduct(
      Pair(qqService, qqServiceCompanion)
      , Pair(qqClient, qqClientCompanion)
      , Pair(qqWrapped, qqWrappedCompanion)
      , Defs(qqBaseCompanion, input, output)
      , List(runtime.Import.from(runtime.Pkg.language, "higherKinds"), rt.services.`import`)
    )

    ext.extend(FullServiceContext(sp, decls), out, _.handleService)
  }
}
