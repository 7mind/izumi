package izumi.idealingua.translator.togolang.extensions

import izumi.idealingua.translator.togolang.GLTContext

class GoLangTranslatorExtensions(ctx: GLTContext, extensions: Seq[GoLangTranslatorExtension]) {
  def extend[S, P]
  (
    source: S
    , entity: P
    , entityTransformer: GoLangTranslatorExtension => (GLTContext, S, P) => P
  ): P = {
    extensions.foldLeft(entity) {
      case (acc, v) =>
        entityTransformer(v)(ctx, source, acc)
    }
  }
}
