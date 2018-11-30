package com.github.pshirshov.izumi.idealingua.model.loader

import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.IL

final case class LoadedModel private(definitions: Seq[IL.Val]) {
  def ++(other: LoadedModel): LoadedModel = {
    LoadedModel((definitions ++ other.definitions).distinct)
  }
}
