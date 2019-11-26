package izumi.idealingua.translator.toscala.types

import izumi.idealingua.model.common.{IndefiniteId, TypeName, TypePath}
import izumi.idealingua.model.il.ast.typed.Service
import izumi.idealingua.translator.toscala.STContext

import scala.meta._

final case class ServiceContext(ctx: STContext, svc: Service) {

  object BIO {
    val n = q"_F"
  }

  object F {
    val t = t"Or"
    val p = tparam"Or[_, _]"
  }

  object Ctx {
    val t = t"C"
    val p = tparam"C"
  }

  val typeName: TypeName = svc.id.name


  private val pkg: Term.Ref = svc.id.domain.toPackage.foldLeft(Term.Name("_root_") : Term.Ref) {
    case (acc, v) =>
      Term.Select(acc, Term.Name(v))
  }

  val methodImport = Import(List(Importer(pkg, List(Importee.Rename(Name(typeName), Name("_M"))))))

  val basePath = TypePath(svc.id.domain, Seq(typeName))
  val svcBaseTpe: ScalaType = ctx.conv.toScala(IndefiniteId(svc.id.domain.toPackage, s"$typeName"))

  private def typeId(name: String): ScalaType = {
    ctx.conv.toScala(IndefiniteId(svc.id.domain.toPackage, name))
  }

  val svcServerTpe: ScalaType = typeId(s"${typeName}Server")
  val svcClientTpe: ScalaType = typeId(s"${typeName}Client")

  val svcWrappedServerTpe: ScalaType = typeId(s"${typeName}WrappedServer")
  val svcWrappedClientTpe: ScalaType = typeId(s"${typeName}WrappedClient")

  val svcMethods: ScalaType = svcBaseTpe
  val svcCodecs: ScalaType = typeId(s"${typeName}Codecs")
}
