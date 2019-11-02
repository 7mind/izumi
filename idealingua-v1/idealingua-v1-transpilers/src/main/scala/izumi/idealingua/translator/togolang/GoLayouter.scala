package izumi.idealingua.translator.togolang

import izumi.idealingua.model.output.{Module, ModuleId}
import izumi.idealingua.model.publishing.manifests.{GoLangBuildManifest, GoProjectLayout}
import izumi.idealingua.translator.CompilerOptions.GoTranslatorOptions
import izumi.idealingua.translator.{ExtendedModule, Layouted, Translated, TranslationLayouter}

class GoLayouter(options: GoTranslatorOptions) extends TranslationLayouter {


  override def layout(outputs: Seq[Translated]): Layouted = {


    val modules = outputs.flatMap {
      out =>
        out.modules.map(m => ExtendedModule.DomainModule(out.typespace.domain.id, m))
    }
    val rtModules = toRuntimeModules(options)

    val allModules = modules ++ rtModules

    val out = options.manifest.layout match {
      case GoProjectLayout.REPOSITORY =>
        val prefix = GoLangBuildManifest.importPrefix(options.manifest).split('/')
        allModules.map {
          case ExtendedModule.DomainModule(domain, module) =>
            ExtendedModule.DomainModule(domain, withPrefix(module, prefix))
          case ExtendedModule.RuntimeModule(module) =>
            ExtendedModule.RuntimeModule(withPrefix(module, prefix))
        }

      case GoProjectLayout.PLAIN =>
        allModules

    }


    Layouted(out)
  }

  private def withPrefix(m: Module, prefix: Seq[String]): Module = Module(ModuleId(prefix ++ m.id.path, m.id.name), m.content)

}
