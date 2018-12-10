package com.github.pshirshov.izumi.idealingua.model.loader

import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.TopLevelDefn

final case class LoadedModel private(definitions: Seq[TopLevelDefn]) {
  def ++(other: LoadedModel): LoadedModel = {
    LoadedModel(definitions ++ other.definitions)
  }
}
