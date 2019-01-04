package com.github.pshirshov.izumi.idealingua.translator.toscala

import com.github.pshirshov.izumi.idealingua.translator.CompilerOptions.ScalaTranslatorOptions
import com.github.pshirshov.izumi.idealingua.translator.{Layouted, Translated, TranslationLayouter}

class ScalaLayouter(options: ScalaTranslatorOptions) extends TranslationLayouter {
  override def layout(outputs: Seq[Translated]): Layouted = {
    Layouted(withRuntime(options, outputs))
  }
}
