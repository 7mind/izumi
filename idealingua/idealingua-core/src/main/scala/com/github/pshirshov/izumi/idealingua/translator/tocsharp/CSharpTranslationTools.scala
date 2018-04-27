package com.github.pshirshov.izumi.idealingua.translator.tocsharp

import com.github.pshirshov.izumi.idealingua.model.common.TypeId

import scala.meta._


class CSharpTranslationTools(ctx: CSTContext) {
  def idToParaName(id: TypeId) = Term.Name(id.name.toLowerCase)
}
