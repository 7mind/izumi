package izumi.idealingua.translator

import izumi.idealingua.model.loader.LoadedDomain
import izumi.idealingua.translator.tocsharp.CSharpTranslatorDescriptor
import izumi.idealingua.translator.togolang.GoTranslatorDescriptor
import izumi.idealingua.translator.toscala.ScalaTranslatorDescriptor
import izumi.idealingua.translator.totypescript.TypescriptTranslatorDescriptor

class TypespaceCompilerBaseFacade(options: UntypedCompilerOptions) {
  def compile(toCompile: Seq[LoadedDomain.Success]): Layouted = {
    val descriptor = TypespaceCompilerBaseFacade.descriptor(options.language)
    val compiled = toCompile.map {
      loaded =>
        descriptor.make(loaded.typespace, options).translate()
    }

    val hook = descriptor.makeHook(options)

    val finalized = hook.layout(compiled)
    finalized
  }
}

object TypespaceCompilerBaseFacade {
  def descriptor(language: IDLLanguage): TranslatorDescriptor[_] = descriptorsMap(language)

  val descriptors: Seq[TranslatorDescriptor[_]] = Seq(
    ScalaTranslatorDescriptor,
    GoTranslatorDescriptor,
    TypescriptTranslatorDescriptor,
    CSharpTranslatorDescriptor,
  )

  private def descriptorsMap: Map[IDLLanguage, TranslatorDescriptor[_]] = descriptors.map(d => d.language -> d).toMap
}

