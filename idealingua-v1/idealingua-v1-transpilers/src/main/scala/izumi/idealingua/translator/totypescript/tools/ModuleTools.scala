package izumi.idealingua.translator.totypescript.tools

import izumi.fundamentals.platform.strings.IzString._
import izumi.idealingua
import izumi.idealingua.model.common.{DomainId, TypeId}
import izumi.idealingua.model.common.TypeId.{BuzzerId, ServiceId}
import izumi.idealingua.model.il.ast.typed.TypeDef
import izumi.idealingua.model.output.{Module, ModuleId}
import izumi.idealingua.translator.totypescript.products.RenderableCogenProduct

class ModuleTools() {
  def toSource(id: DomainId, moduleId: ModuleId, product: RenderableCogenProduct): Seq[Module] = {
    product match {
      case p if p.isEmpty =>
        Seq.empty

      case _ =>
        val code = (product.preamble +:  product.render.map(_.toString())).mkString("\n")
        val header = product.renderHeader.map(_.toString()).mkString("\n")
        val content: String = withPackage(id.toPackage, header, code)
        Seq(Module(moduleId, content))
    }
  }

  def withPackage(pkg: idealingua.model.common.Package, header: String, code: String): String = {
    val content = if (pkg.isEmpty) {
      code
    } else {
      s"""// Auto-generated, any modifications may be overwritten in the future.
          |${header}
          |
          |$code
       """.stripMargin
    }
    content.densify()
  }

  def toModuleId(defn: TypeDef): ModuleId = {
    toModuleId(defn.id)
    /*
    defn match {
      case i: Alias =>
        val concrete = i.id
        ModuleId(concrete.pkg, s"${concrete.pkg.last}.ts")

      case other =>
        toModuleId(other.id)
    }
    */
  }

  def toModuleId(id: TypeId): ModuleId = {
    ModuleId(id.path.toPackage, s"${id.name}.ts")
  }

  def toModuleId(id: ServiceId): ModuleId = {
    ModuleId(id.domain.toPackage, s"${id.name}.ts")
  }

  def toModuleId(id: BuzzerId): ModuleId = {
    ModuleId(id.domain.toPackage, s"${id.name}.ts")
  }
}
