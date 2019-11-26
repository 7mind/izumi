package izumi.idealingua.translator.toscala.extensions

import izumi.idealingua.model.output.Module
import izumi.idealingua.translator.toscala.STContext

class ScalaTranslatorExtensions(ctx: STContext, extensions: Seq[ScalaTranslatorExtension]) {
  def extend(modules: Seq[Module]): Seq[Module] = {
    extensions.foldLeft(modules) {
      case (acc, ext) =>
        ext.handleModules(ctx, acc)
    }
  }

  def extend[S, P]
  (
    source: S
    , entity: P
    , entityTransformer: ScalaTranslatorExtension => (STContext, S, P) => P
  ): P = {
    extensions.foldLeft(entity) {
      case (acc, v) =>
        entityTransformer(v)(ctx, source, acc)
    }
  }
}
