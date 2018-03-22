package com.github.pshirshov.izumi.idealingua.il

case class LoadedModel private(definitions: Seq[IL.Val]) {
  def ++(other: LoadedModel): LoadedModel = {
    LoadedModel((definitions ++ other.definitions).distinct)
  }
}

