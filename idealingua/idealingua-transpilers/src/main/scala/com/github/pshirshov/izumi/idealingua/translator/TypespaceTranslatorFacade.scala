package com.github.pshirshov.izumi.idealingua.translator

import com.github.pshirshov.izumi.idealingua.model.output.Module
import com.github.pshirshov.izumi.idealingua.model.typespace.Typespace
import com.github.pshirshov.izumi.idealingua.translator.tocsharp.CSharpTranslator
import com.github.pshirshov.izumi.idealingua.translator.togolang.GoLangTranslator
import com.github.pshirshov.izumi.idealingua.translator.toscala.ScalaTranslator
import com.github.pshirshov.izumi.idealingua.translator.totypescript.TypeScriptTranslator

class TypespaceTranslatorFacade(typespace: Typespace) {
  def translate(options: UntypedCompilerOptions): Seq[Module] = {
    options.language match {
      case IDLLanguage.Scala =>
        new ScalaTranslator(typespace, CompilerOptions.from(options)).translate()
      case IDLLanguage.Go =>
        new GoLangTranslator(typespace, CompilerOptions.from(options)).translate()
      case IDLLanguage.Typescript =>
        new TypeScriptTranslator(typespace, CompilerOptions.from(options)).translate()
      case IDLLanguage.CSharp =>
        new CSharpTranslator(typespace, CompilerOptions.from(options)).translate()
    }
  }
}

object TypespaceTranslatorFacade {
  def extensions: Map[IDLLanguage, Seq[TranslatorExtension]] = Map(
    IDLLanguage.Scala -> ScalaTranslator.defaultExtensions
    , IDLLanguage.Typescript -> TypeScriptTranslator.defaultExtensions
    , IDLLanguage.Go -> GoLangTranslator.defaultExtensions
    , IDLLanguage.CSharp -> CSharpTranslator.defaultExtensions
  )
}
