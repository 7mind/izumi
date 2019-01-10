package com.github.pshirshov.izumi.idealingua.translator.togolang

import com.github.pshirshov.izumi.idealingua.translator.CompilerOptions.GoTranslatorOptions
import com.github.pshirshov.izumi.idealingua.translator.{ExtendedModule, Layouted, Translated, TranslationLayouter}

class GoLayouter(options: GoTranslatorOptions) extends TranslationLayouter {


  override def layout(outputs: Seq[Translated]): Layouted = {


    val modules = outputs.flatMap {
      out =>
        out.modules.map(m => ExtendedModule.DomainModule(out.typespace.domain.id, m))
    }
    val rtModules = toRuntimeModules(options)

    val allModules = modules ++ rtModules

//    val scoped = options.manifest.layout == GoProjectLayout.REPOSITORY
//    val out = if (scoped) {
//      val prefix = GoLangBuildManifest.importPrefix(options.manifest).split('/')
//      allModules.map {
//        case ExtendedModule.DomainModule(domain, module) =>
//          ExtendedModule.DomainModule(domain, withPrefix(module, prefix))
//        case ExtendedModule.RuntimeModule(module) =>
//          ExtendedModule.RuntimeModule(withPrefix(module, prefix))
//      }
//    } else {
//      allModules
//    }

    Layouted(allModules)
  }

//  private def withPrefix(m: Module, prefix: Seq[String]): Module = Module(ModuleId(prefix ++ m.id.path, m.id.name), m.content)

}
