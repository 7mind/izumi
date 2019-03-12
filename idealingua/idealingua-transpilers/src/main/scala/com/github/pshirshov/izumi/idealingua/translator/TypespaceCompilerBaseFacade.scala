package com.github.pshirshov.izumi.idealingua.translator

import com.github.pshirshov.izumi.idealingua.model.loader.LoadedDomain

class TypespaceCompilerBaseFacade(options: UntypedCompilerOptions) {
  def compile(toCompile: Seq[LoadedDomain.Success]): Layouted = {
    val descriptor = TypespaceCompilerBaseFacade.descriptor(options.language)
    val compiled = toCompile.map {
      loaded =>
        descriptor.translate(options , loaded.typespace)
    }
    val finalized = descriptor.layout(compiled)
    finalized
  }
}

object TypespaceCompilerBaseFacade {
  def descriptor(language: IDLLanguage): TranslatorDescriptor = descriptorsMap(language)

  val descriptors: Seq[TranslatorDescriptor] = Seq(

  )

  private def descriptorsMap: Map[IDLLanguage, TranslatorDescriptor] = descriptors.map(d => d.language -> d).toMap
}









