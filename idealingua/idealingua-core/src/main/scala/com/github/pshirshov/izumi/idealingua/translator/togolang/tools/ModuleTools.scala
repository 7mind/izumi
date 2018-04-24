package com.github.pshirshov.izumi.idealingua.translator.togolang.tools

import com.github.pshirshov.izumi.idealingua
import com.github.pshirshov.izumi.idealingua.model.common.TypeId.ServiceId
import com.github.pshirshov.izumi.idealingua.model.common.{DomainId, IndefiniteId, TypeId}
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.TypeDef
import com.github.pshirshov.izumi.idealingua.model.output.{Module, ModuleId}
import com.github.pshirshov.izumi.idealingua.translator.togolang.products.RenderableCogenProduct
import com.github.pshirshov.izumi.fundamentals.platform.strings.IzString._

class ModuleTools() {
  def toSource(id: DomainId, moduleId: ModuleId, testModuleId: ModuleId, product: RenderableCogenProduct): Seq[Module] = {
    product match {
      case p if p.isEmpty =>
        Seq.empty

      case _ =>
        val code = product.render.mkString("\n")
        val header = product.renderHeader.mkString("\n")
        val content: String = withPackage(id.toPackage, header, code)

        val tests = product.renderTests
        if (tests.isEmpty) {
          Seq(Module(moduleId, content))
        } else {
          Seq(
            Module(moduleId, content),
            Module(testModuleId, withPackage(id.toPackage, "", tests.mkString("\n")))
          )
        }
    }
  }

  def withPackage(pkg: idealingua.model.common.Package, header: String, code: String): String = {
    val content = if (pkg.isEmpty) {
      code
    } else {
      s"""// Auto-generated, any modifications may be overwritten in the future.
         |package ${pkg.last}
         |${ if (header.length > 0) s"\n$header\n" else ""}
         |${code}
       """.stripMargin
    }
    content.densify()
  }

  def toTestModuleId(defn: TypeDef): ModuleId = {
    toTestModuleId(defn.id)
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

  def toTestModuleId(id: TypeId): ModuleId = {
    ModuleId(id.path.toPackage, s"${id.name}_test.go")
  }

  def toModuleId(id: TypeId): ModuleId = {
    ModuleId(id.path.toPackage, s"${id.name}.go")
  }

  def toModuleId(id: ServiceId): ModuleId = {
    ModuleId(id.domain.toPackage, s"${id.name}.go")
  }

  def toTestModuleId(id: ServiceId): ModuleId = {
    ModuleId(id.domain.toPackage, s"${id.name}_test.go")
  }
}
