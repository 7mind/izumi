package com.github.pshirshov.izumi.idealingua.translator.tocsharp.tools

import com.github.pshirshov.izumi.fundamentals.platform.strings.IzString._
import com.github.pshirshov.izumi.idealingua
import com.github.pshirshov.izumi.idealingua.model.common.TypeId.ServiceId
import com.github.pshirshov.izumi.idealingua.model.common.{DomainId, TypeId}
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.TypeDef
import com.github.pshirshov.izumi.idealingua.model.output.{Module, ModuleId}
import com.github.pshirshov.izumi.idealingua.translator.tocsharp.products.RenderableCogenProduct

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
         |namespace ${pkg.mkString(".")} {
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
    ModuleId(id.path.toPackage, s"${id.name}.cs")
  }

  def toModuleId(id: ServiceId): ModuleId = {
    ModuleId(id.domain.toPackage, s"${id.name}.cs")
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
    Seq(Module(moduleId, text))
  }

  def toTestModuleId(id: TypeId): ModuleId = {
    ModuleId(id.path.toPackage, s"${id.name}_test.cs")
  }

  def toTestModuleId(id: ServiceId): ModuleId = {
    ModuleId(id.domain.toPackage, s"${id.name}_test.cs")
  }
}
