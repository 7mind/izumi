package com.github.pshirshov.izumi.idealingua.translator.tocsharp.extensions

import com.github.pshirshov.izumi.idealingua.translator.tocsharp.CSTContext

class CSharpTranslatorExtensions(ctx: CSTContext, extensions: Seq[CSharpTranslatorExtension]) {
  def extend[S, P]
  (
    source: S
    , entity: P
    , entityTransformer: CSharpTranslatorExtension => (CSTContext, S, P) => P
  ): P = {
    extensions.foldLeft(entity) {
      case (acc, v) =>
        entityTransformer(v)(ctx, source, acc)
    }
  }
}
