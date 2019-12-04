package izumi.idealingua.translator.toscala

import _root_.io.circe.{DecodingFailure, Json}
import izumi.functional.bio.BIO
import izumi.idealingua.model.il.ast.typed.DefMethod.RPCMethod
import izumi.idealingua.model.il.ast.typed.Service
import izumi.idealingua.translator.toscala.products.CogenProduct.CogenServiceProduct
import izumi.idealingua.translator.toscala.products.RenderableCogenProduct
import izumi.idealingua.translator.toscala.types.{runtime, _}

import scala.meta._

class ServiceRenderer(ctx: STContext) {

  import ctx._

  def renderService(svc: Service): RenderableCogenProduct = {
    val c = ServiceContext(ctx, svc)

    val decls = svc.methods
      .collect { case c: RPCMethod => c }
      .map(ServiceMethodProduct(ctx, c, _))

    val qqServer =
      q"""trait ${c.svcServerTpe.typeName}[Or[+_, +_], ${c.Ctx.p}] {
            type Just[+T] = Or[Nothing, T]
            ..${decls.map(_.defnServer)}
          }"""

    val qqClient =
      q"""trait ${c.svcClientTpe.typeName}[Or[+_, +_]] {
            type Just[+T] = Or[Nothing, T]
            ..${decls.map(_.defnClient)}
          }"""

    val qqClientWrapped =
      q"""class ${c.svcWrappedClientTpe.typeName}[Or[+_, +_] : IRTBIO](_dispatcher: ${rt.IRTDispatcher.parameterize(List(c.F.t)).typeFull})
               extends ${c.svcClientTpe.parameterize(List(c.F.t)).init()} {
               final val _F: IRTBIO[${c.F.t}] =  implicitly
               ${c.methodImport}

               ..${decls.map(_.defnClientWrapped)}
          }"""

    val qqClientWrappedCompanion =
      q"""
         object ${c.svcWrappedClientTpe.termName} extends ${rt.IRTWrappedClient.init()} {
           val allCodecs: Map[${rt.IRTMethodId.typeName}, IRTCirceMarshaller] = {
             Map(..${decls.map(_.defnCodecRegistration)})
           }
         }
       """

    val qqServerWrapped =
      q"""class ${c.svcWrappedServerTpe.typeName}[Or[+_, +_] : IRTBIO, ${c.Ctx.p}](
              _service: ${c.svcServerTpe.typeName}[${c.F.t}, ${c.Ctx.t}]
            )
               extends IRTWrappedService[${c.F.t}, ${c.Ctx.t}] {
            final val _F: IRTBIO[${c.F.t}] = implicitly

            final val serviceId: ${rt.IRTServiceId.typeName} = ${c.svcMethods.termName}.serviceId

            val allMethods: Map[${rt.IRTMethodId.typeName}, IRTMethodWrapper[${c.F.t}, ${c.Ctx.t}]] = {
              Seq[IRTMethodWrapper[${c.F.t}, ${c.Ctx.t}]](..${decls.map(_.defnMethodRegistration)})
                .map(m => m.signature.id -> m)
                .toMap
            }

            ..${decls.map(_.defnServerWrapped)}
          }"""

    val qqServerWrappedCompanion =
      q"""
         object ${c.svcWrappedServerTpe.termName} {
         }
       """

    val qqServiceMethods =
      q"""
         object ${c.svcMethods.termName} {
           final val serviceId: ${rt.IRTServiceId.typeName} = ${rt.IRTServiceId.termName}(${Lit.String(c.typeName)})

           ..${decls.map(_.defnMethod)}
           ..${decls.flatMap(_.defStructs)}
         }
       """

    val qqServiceCodecs =
      q"""
         object ${c.svcCodecs.termName} {
          ..${decls.map(_.defnCodec)}
         }
       """

    type Dummy[+X, +Y] = Nothing
    val out = CogenServiceProduct(
      qqServer
      , qqClient
      , CogenServiceProduct.Pair(qqServerWrapped, qqServerWrappedCompanion)
      , CogenServiceProduct.Pair(qqClientWrapped, qqClientWrappedCompanion)
      , qqServiceMethods
      , qqServiceCodecs
      , List(
        runtime.Import.from(runtime.Pkg.language, "higherKinds")
        , runtime.Import[BIO[Dummy]](Some("IRTBIO"))
        , runtime.Import[Json](Some("IRTJson"))
        , runtime.Import[DecodingFailure](Some("IRTDecodingFailure"))
        , runtime.Pkg.of[_root_.io.circe.syntax.EncoderOps[Nothing]].`import`
        , rt.services.`import`
      )
    )

    ext.extend(FullServiceContext(c, decls), out, _.handleService)
  }
}
