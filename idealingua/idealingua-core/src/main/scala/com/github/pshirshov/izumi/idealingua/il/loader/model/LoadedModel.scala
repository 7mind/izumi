package com.github.pshirshov.izumi.idealingua.il.loader.model

import com.github.pshirshov.izumi.idealingua.il.parser.IL

final case class LoadedModel private(definitions: Seq[IL.Val]) {
  def ++(other: LoadedModel): LoadedModel = {
    LoadedModel((definitions ++ other.definitions).distinct)
  }
}

