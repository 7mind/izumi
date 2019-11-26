package izumi.idealingua.translator.totypescript.extensions

import izumi.idealingua.translator.totypescript.TSTContext

class TypeScriptTranslatorExtensions(ctx: TSTContext, extensions: Seq[TypeScriptTranslatorExtension]) {
  def extend[S, P]
  (
    source: S
    , entity: P
    , entityTransformer: TypeScriptTranslatorExtension => (TSTContext, S, P) => P
  ): P = {
    extensions.foldLeft(entity) {
      case (acc, v) =>
        entityTransformer(v)(ctx, source, acc)
    }
  }
}
