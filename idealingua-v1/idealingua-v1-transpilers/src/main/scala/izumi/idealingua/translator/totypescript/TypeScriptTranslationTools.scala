package izumi.idealingua.translator.totypescript

import izumi.idealingua.model.common.TypeId

import scala.meta._


class TypeScriptTranslationTools() {
  def idToParaName(id: TypeId) = Term.Name(id.name.toLowerCase)
}
