package com.github.pshirshov.izumi.idealingua.model.il.ast.raw.domains

import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.defns.RawNodeMeta

final case class ImportedId(name: String, as: Option[String], meta: RawNodeMeta) {
  def importedAs: String = as.getOrElse(name)
}
