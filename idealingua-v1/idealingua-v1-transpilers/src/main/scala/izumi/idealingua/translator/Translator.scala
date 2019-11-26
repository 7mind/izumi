package izumi.idealingua.translator

import izumi.idealingua.model.output.Module
import izumi.idealingua.model.typespace.Typespace

case class Translated(typespace: Typespace, modules: Seq[Module])

trait Translator {
  def translate(): Translated
}
