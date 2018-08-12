package com.github.pshirshov.izumi.idealingua.translator.toscala

import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.DefMethod.RPCMethod
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.Service
import com.github.pshirshov.izumi.idealingua.translator.toscala.products.CogenProduct.CogenServiceProduct
import com.github.pshirshov.izumi.idealingua.translator.toscala.products.RenderableCogenProduct
import com.github.pshirshov.izumi.idealingua.translator.toscala.types.{runtime, _}
import scalaz.zio.IO

import scala.meta._
import _root_.io.circe.Json

class ServiceRenderer(ctx: STContext) {

  import ctx._

  def renderService(svc: Service): RenderableCogenProduct = {
    val c = ServiceContext(ctx, svc)

    val decls = svc.methods
      .collect({ case c: RPCMethod => c })
      .map(ServiceMethodProduct(ctx, c, _))

    val qqServer =
      q"""trait ${c.svcServerTpe.typeName}[${c.F.p}, ${c.Ctx.p}]
              extends ${rt.WithResult.parameterize(List(c.F.t)).init()} {
            ..${decls.map(_.defnServer)}
          }"""

    val qqClient =
      q"""trait ${c.svcClientTpe.typeName}[${c.F.p}]
              extends ${rt.WithResult.parameterize(List(c.F.t)).init()} {
            ..${decls.map(_.defnClient)}
          }"""

    val qqClientWrapped =
      q"""class ${c.svcWrappedClientTpe.typeName}(_dispatcher: ${rt.IRTDispatcher.typeName})
               extends ${c.svcClientTpe.parameterize(List(c.BIO.t)).init()} with ${rt.WithResultZio.init()} {
               private final val _M = ${c.svcMethods.termFull}
               ..${decls.map(_.defnClientWrapped)}
          }"""

    val qqClientWrappedCompanion =
      q"""
         object ${c.svcWrappedClientTpe.termName} extends ${rt.IRTWrappedClient.parameterize(List(c.BIO.t)).init()} {
           val allCodecs: Map[${rt.IRTMethodId.typeName}, IRTCirceMarshaller[${c.BIO.t}]] = {
             Map(..${decls.map(_.defnCodecRegistration)})
           }
         }
       """

    val qqServerWrapped =
      q"""class ${c.svcWrappedServerTpe.typeName}[${c.Ctx.p}](_service: ${c.svcServerTpe.typeName}[${c.BIO.t}, ${c.Ctx.t}] with ${rt.WithResultZio.typeName})
               extends IRTWrappedService[${c.BIO.t}, ${c.Ctx.t}] with ${rt.WithResultZio.init()} {
            final val serviceId: ${rt.IRTServiceId.typeName} = ${c.svcMethods.termName}.serviceId

            val allMethods: Map[${rt.IRTMethodId.typeName}, IRTMethodWrapper[${c.BIO.t}, ${c.Ctx.t}]] = {
              Seq(..${decls.map(_.defnMethodRegistration)})
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

    val out = CogenServiceProduct(
      qqServer
      , qqClient
      , CogenServiceProduct.Pair(qqServerWrapped, qqServerWrappedCompanion)
      , CogenServiceProduct.Pair(qqClientWrapped, qqClientWrappedCompanion)
      , qqServiceMethods
      , qqServiceCodecs
      , List(
        runtime.Import.from(runtime.Pkg.language, "higherKinds")
        , runtime.Import[IO[Nothing, Nothing]](Some("IRTBIO"))
        , runtime.Import[Json](Some("IRTJson"))
        , runtime.Pkg.of[_root_.io.circe.syntax.EncoderOps[Nothing]].`import`
        , rt.services.`import`
      )
    )

    ext.extend(FullServiceContext(c, decls), out, _.handleService)
  }
}
