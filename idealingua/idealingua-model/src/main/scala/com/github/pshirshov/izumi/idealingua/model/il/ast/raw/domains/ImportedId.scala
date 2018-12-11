package com.github.pshirshov.izumi.idealingua.model.il.ast.raw.domains

final case class ImportedId(name: String, as: Option[String]) {
  def importedName: String = as.getOrElse(name)
}
