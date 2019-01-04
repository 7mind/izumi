package com.github.pshirshov.izumi.idealingua.translator.togolang

import com.github.pshirshov.izumi.idealingua.model.output.{Module, ModuleId}
import com.github.pshirshov.izumi.idealingua.translator.CompilerOptions.GoTranslatorOptions
import com.github.pshirshov.izumi.idealingua.translator.{ExtendedModule, Layouted, Translated, TranslationLayouter}

class GoLayouter(options: GoTranslatorOptions) extends TranslationLayouter {
  override def layout(outputs: Seq[Translated]): Layouted = {
    val modules = outputs.flatMap {
      out =>
        val mm = if (options.manifest.isDefined && options.manifest.get.useRepositoryFolders) {
          out.modules.map(m => Module(ModuleId(options.manifest.get.repository.split("/") ++ m.id.path, m.id.name), m.content))
        } else {
          out.modules
        }

        mm.map(m => ExtendedModule.DomainModule(out.typespace.domain.id, m))
    }

    Layouted(toRuntimeModules(options) ++ modules)
  }
}
