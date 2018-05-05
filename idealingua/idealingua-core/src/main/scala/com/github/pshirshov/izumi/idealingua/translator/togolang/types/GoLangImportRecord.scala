package com.github.pshirshov.izumi.idealingua.translator.togolang.types

import com.github.pshirshov.izumi.idealingua.model.common.Package
import com.github.pshirshov.izumi.idealingua.model.common.TypeId

final case class GoLangImportRecord(id: TypeId, importName: String, pkg: Package) {
  def renderImport(): String = {
    if(pkg.isEmpty) {
      return ""
    }

    (if (importName.length == 0 || pkg.last == importName) "" else s"$importName ") +
      "\"" + pkg.mkString("/") + "\""
  }
}

object GoLangImportRecord {
  def apply(id: TypeId, importName: String, pkg: Package): GoLangImportRecord =
    new GoLangImportRecord(id, importName, pkg)
}
