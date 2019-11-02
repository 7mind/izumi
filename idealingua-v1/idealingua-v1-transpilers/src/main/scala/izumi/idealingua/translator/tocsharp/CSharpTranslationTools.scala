package izumi.idealingua.translator.tocsharp

import izumi.idealingua.model.common.TypeId

import scala.meta._

class CSharpTranslationTools() {
  def idToParaName(id: TypeId) = Term.Name(id.name.toLowerCase)
}
