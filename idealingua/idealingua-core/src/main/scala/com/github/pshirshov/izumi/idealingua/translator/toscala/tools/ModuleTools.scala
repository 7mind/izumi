package com.github.pshirshov.izumi.idealingua.translator.toscala.tools

import com.github.pshirshov.izumi.idealingua
import com.github.pshirshov.izumi.idealingua.model.common.TypeId.ServiceId
import com.github.pshirshov.izumi.idealingua.model.common.{IndefiniteId, TypeId}
import com.github.pshirshov.izumi.idealingua.model.il.ILAst
import com.github.pshirshov.izumi.idealingua.model.il.ILAst.Alias
import com.github.pshirshov.izumi.idealingua.model.output.{Module, ModuleId}
import com.github.pshirshov.izumi.idealingua.translator.toscala.types.runtime.IDLRuntimeTypes

import scala.meta.Defn

class ModuleTools(rt: IDLRuntimeTypes.type) {
  def toSource(id: IndefiniteId, moduleId: ModuleId, traitDef: Seq[Defn]): Seq[Module] = {
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
         |import _root_.${rt.modelPkg}._
         |import _root_.${rt.runtimePkg}._
         |
         |$code
       """.stripMargin
    }
    content
  }

  def toModuleId(defn: ILAst): ModuleId = {
    defn match {
      case i: Alias =>
        val concrete = i.id
        ModuleId(concrete.pkg, s"${concrete.pkg.last}.scala")

      case other =>
        toModuleId(other.id)
    }
  }

  def toModuleId(id: TypeId): ModuleId = {
    ModuleId(id.pkg, s"${id.name}.scala")
  }

  def toModuleId(id: ServiceId): ModuleId = {
    ModuleId(id.pkg, s"${id.name}.scala")
  }
}
