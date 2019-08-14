package izumi.idealingua.translator.togolang.types

import izumi.idealingua.model.common.Package
import izumi.idealingua.model.common.TypeId

final case class GoLangImportRecord(id: TypeId, importName: String, pkg: Package) {
  def renderImport(prefix: String): String = {
    if(pkg.isEmpty) {
      return ""
    }

    val directImports = Seq(
      "time",
      "regexp",
      "encoding/json"
    )

    val pkgStr = pkg.mkString("/")

    val pre = if (prefix.nonEmpty && !directImports.contains(pkgStr))
      if (!prefix.endsWith("/") && !prefix.endsWith("\\")) prefix + "/" else prefix
    else
      ""

    (if (importName.length == 0 || pkg.last == importName) "" else s"$importName ") +
      "\"" + pre + pkgStr + "\""
  }
}

object GoLangImportRecord {
  def apply(id: TypeId, importName: String, pkg: Package): GoLangImportRecord =
    new GoLangImportRecord(id, importName, pkg)
}
