package izumi.idealingua.model.loader

import izumi.idealingua.model.il.ast.raw.defns.RawTopLevelDefn

final case class LoadedModel private(definitions: Seq[RawTopLevelDefn]) {
  def ++(other: LoadedModel): LoadedModel = {
    LoadedModel(definitions ++ other.definitions)
  }
}
