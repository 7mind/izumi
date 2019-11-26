package izumi.idealingua.translator.togolang

import izumi.idealingua.model.common.TypeId

import scala.meta._


class GoLangTranslationTools() {
  def idToParaName(id: TypeId) = Term.Name(id.name.toLowerCase)
}
