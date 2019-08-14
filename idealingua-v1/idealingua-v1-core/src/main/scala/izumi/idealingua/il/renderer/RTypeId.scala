package izumi.idealingua.il.renderer

import izumi.functional.Renderable
import izumi.idealingua.model.common
import izumi.idealingua.model.common._

class RTypeId(context: IDLRenderingContext) extends Renderable[TypeId] {

  override def render(value: TypeId): String = {
    value match {
      case g: Generic =>
        s"${renderTypeName(g.path, g.name)}${g.args.map(render).mkString("[", ", ", "]")}"

      case t =>
        renderTypeName(t.path, t.name)
    }
  }

  private def renderTypeName(pkg: TypePath, name: TypeName) = {
    pkg.domain match {
      case DomainId.Builtin =>
        name

      case _ =>
        Seq(renderPkg(pkg.toPackage), name).filterNot(_.isEmpty).mkString("#")
    }
  }

  private def renderPkg(value: common.Package): String = {
    minimize(value).mkString(".")
  }

  private def minimize(value: common.Package): common.Package = {
    val domainPkg = context.domain.id.toPackage
    if (value == domainPkg) {
      Seq.empty
    } else if (value.nonEmpty && domainPkg.last == value.head) {
      value.tail
    } else {
      value
    }
  }
}
