package com.github.pshirshov.izumi.idealingua.translator.totypescript.tools

import com.github.pshirshov.izumi.idealingua
import com.github.pshirshov.izumi.idealingua.model.common.TypeId.ServiceId
import com.github.pshirshov.izumi.idealingua.model.common.{IndefiniteId, TypeId}
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.TypeDef
import com.github.pshirshov.izumi.idealingua.model.output.{Module, ModuleId}
import com.github.pshirshov.izumi.idealingua.translator.totypescript.products.RenderableCogenProduct
import com.github.pshirshov.izumi.fundamentals.platform.strings.IzString._

class ModuleTools() {
  def toSource(id: IndefiniteId, moduleId: ModuleId, product: RenderableCogenProduct): Seq[Module] = {
    product match {
      case p if p.isEmpty =>
        Seq.empty

      case _ =>
        val code = (product.preamble +:  product.render.map(_.toString())).mkString("\n")
        val header = product.renderHeader.map(_.toString()).mkString("\n")
        val content: String = withPackage(id.pkg, header, code)
        Seq(Module(moduleId, content))
    }
  }

  def withPackage(pkg: idealingua.model.common.Package, header: String, code: String): String = {
    val content = if (pkg.isEmpty) {
      code
    } else {
      s"""// Auto-generated, any modifications may be overwritten in the future.
          |${header}
          |namespace ${pkg.mkString(".")} {
          |
          |${code.shift(4)}
          |
          |}
       """.stripMargin
    }
    content
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
    ModuleId(id.pkg, s"${id.name}.ts")
  }

  def toModuleId(id: ServiceId): ModuleId = {
    ModuleId(id.pkg, s"${id.name}.ts")
  }
}
