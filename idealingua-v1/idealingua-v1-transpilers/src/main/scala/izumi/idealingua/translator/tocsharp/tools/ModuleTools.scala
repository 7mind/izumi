package izumi.idealingua.translator.tocsharp.tools

import izumi.fundamentals.platform.strings.IzString._
import izumi.idealingua
import izumi.idealingua.model.common.TypeId.{BuzzerId, ServiceId}
import izumi.idealingua.model.common.{DomainId, TypeId}
import izumi.idealingua.model.il.ast.typed.TypeDef
import izumi.idealingua.model.output.{Module, ModuleId}
import izumi.idealingua.translator.tocsharp.products.RenderableCogenProduct

class ModuleTools() {
  def toSource(id: DomainId, moduleId: ModuleId, product: RenderableCogenProduct): Seq[Module] = {
    product match {
      case p if p.isEmpty =>
        Seq.empty

      case _ =>
        val code = product.render.mkString("\n").shift(4)
        val header = product.renderHeader.mkString("\n")
        val content: String = withPackage(id.toPackage, header, code)

        val tests = product.renderTests
        if (tests.isEmpty) {
          Seq(Module(moduleId, content))
        } else {
          Seq(
            Module(moduleId, content),
          )
        }
    }
  }

  def withPackage(pkg: idealingua.model.common.Package, header: String, code: String): String = {
    val content = if (pkg.isEmpty) {
      code
    } else {
      s"""// Auto-generated, any modifications may be overwritten in the future.
         |${ if (header.length > 0) s"\n$header\n" else ""}
         |namespace ${pkg.map(p => p.capitalize).mkString(".")} {
         |${code}
         |}
       """.stripMargin
    }
    content.densify()
  }

  def toModuleId(defn: TypeDef): ModuleId = {
    toModuleId(defn.id)
  }

  def toModuleId(id: TypeId): ModuleId = {
    ModuleId(id.path.toPackage.map(p => p.capitalize), s"${id.name.capitalize}.cs")
  }

  def toModuleId(id: ServiceId): ModuleId = {
    ModuleId(id.domain.toPackage.map(p => p.capitalize), s"${id.name.capitalize}.cs")
  }

  def toModuleId(id: BuzzerId): ModuleId = {
    ModuleId(id.domain.toPackage.map(p => p.capitalize), s"${id.name.capitalize}.cs")
  }

  def toTestSource(id: DomainId, moduleId: ModuleId, header: String, code: String): Seq[Module] = {
    val text =
      s"""#if IRT_NO_TESTS
         |    // Define IRT_NO_TESTS in case you want to exclude tests from compilation
         |#else
         |
         |${withPackage(id.toPackage, header, code.shift(4))}
         |
         |#endif
       """.stripMargin
    Seq(Module(moduleId, text, Map("scope" -> "test")))
  }

  def toTestModuleId(id: TypeId, prefix: Option[String] = None): ModuleId = {
    ModuleId(Seq("Tests") ++ id.path.toPackage.map(p => p.capitalize), s"${if (prefix.isDefined) prefix.get + id.name else id.name}Tests.cs")
  }

  def toTestModuleId(id: ServiceId): ModuleId = {
    ModuleId(Seq("Tests") ++ id.domain.toPackage.map(p => p.capitalize), s"${id.name}Tests.cs")
  }

  def toTestModuleId(id: BuzzerId): ModuleId = {
    ModuleId(Seq("Tests") ++ id.domain.toPackage.map(p => p.capitalize), s"${id.name}Tests.cs")
  }
}
