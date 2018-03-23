package com.github.pshirshov.izumi.idealingua.translator.toscala

import com.github.pshirshov.izumi.idealingua
import com.github.pshirshov.izumi.idealingua.model.common.Indefinite
import com.github.pshirshov.izumi.idealingua.model.output.{Module, ModuleId}

import scala.meta.Defn

class ModuleTools(rt: IDLRuntimeTypes.type) {
  def toSource(id: Indefinite, moduleId: ModuleId, traitDef: Seq[Defn]): Seq[Module] = {
    if (traitDef.nonEmpty) {
      val code = traitDef.map(_.toString()).mkString("\n\n")
      val content: String = withPackage(id.pkg, code)
      Seq(Module(moduleId, content))
    } else {
      Seq.empty
    }
  }

  def withPackage(pkg: idealingua.model.common.Package, code: String): String = {
    val content = if (pkg.isEmpty) {
      code
    } else {
      s"""package ${pkg.mkString(".")}
         |
         |import scala.language.higherKinds
         |
         |import _root_.${rt.basePkg}._
         |
         |$code
       """.stripMargin
    }
    content
  }
}
