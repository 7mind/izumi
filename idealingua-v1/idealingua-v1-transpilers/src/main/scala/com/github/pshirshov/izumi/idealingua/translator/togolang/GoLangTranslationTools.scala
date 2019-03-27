package com.github.pshirshov.izumi.idealingua.translator.togolang

import com.github.pshirshov.izumi.idealingua.model.common.TypeId

import scala.meta._


class GoLangTranslationTools() {
  def idToParaName(id: TypeId) = Term.Name(id.name.toLowerCase)
}
