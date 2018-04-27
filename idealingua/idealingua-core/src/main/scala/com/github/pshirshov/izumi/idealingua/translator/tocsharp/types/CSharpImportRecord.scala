package com.github.pshirshov.izumi.idealingua.translator.tocsharp.types

import com.github.pshirshov.izumi.idealingua.model.common.Package
import com.github.pshirshov.izumi.idealingua.model.common.TypeId

case class CSharpImportRecord(id: TypeId, importName: String, pkg: Package) {
  def renderImport(): String = {
    if(pkg.isEmpty) {
      return ""
    }
    (if (importName.length == 0 || pkg.last == importName) "" else s"$importName ") +
      "\"" + pkg.mkString("/") + "\""
  }
}

object CSharpImportRecord {
  def apply(id: TypeId, importName: String, pkg: Package): CSharpImportRecord =
    new CSharpImportRecord(id, importName, pkg)
}