package com.github.pshirshov.izumi.idealingua.translator

import com.github.pshirshov.izumi.idealingua.model.output.Module
import com.github.pshirshov.izumi.idealingua.model.typespace.Typespace
import com.github.pshirshov.izumi.idealingua.model.typespace.verification.VerificationRule
import com.github.pshirshov.izumi.idealingua.model.typespace.verification.rules.CyclicImportsRule
import com.github.pshirshov.izumi.idealingua.translator.tocsharp.CSharpTranslator
import com.github.pshirshov.izumi.idealingua.translator.togolang.GoLangTranslator
import com.github.pshirshov.izumi.idealingua.translator.toscala.ScalaTranslator
import com.github.pshirshov.izumi.idealingua.translator.totypescript.TypeScriptTranslator

class TypespaceCompilerBaseFacade(typespace: Typespace, options: UntypedCompilerOptions) {
  def compile(): Seq[Module] = {
    TypespaceCompilerBaseFacade.descriptor(options.language).make(typespace, options).translate()
  }
}

object TypespaceCompilerBaseFacade {
  def descriptor(language: IDLLanguage): TranslatorDescriptor = descriptorsMap(language)

  val descriptors: Seq[TranslatorDescriptor] = Seq(
    ScalaTranslatorDescriptor,
    GoTranslatorDescriptor,
    TypescriptTranslatorDescriptor,
    CSharpTranslatorDescriptor,
  )

  private def descriptorsMap: Map[IDLLanguage, TranslatorDescriptor] = descriptors.map(d => d.language -> d).toMap
}


object ScalaTranslatorDescriptor extends TranslatorDescriptor {
  override def language: IDLLanguage = IDLLanguage.Scala

  override def defaultExtensions: Seq[TranslatorExtension] = ScalaTranslator.defaultExtensions

  override def make(typespace: Typespace, options: UntypedCompilerOptions): Translator = new ScalaTranslator(typespace, CompilerOptions.from(options))

  override def rules: Seq[VerificationRule] = Seq.empty
}

object GoTranslatorDescriptor extends TranslatorDescriptor {
  override def language: IDLLanguage = IDLLanguage.Go

  override def defaultExtensions: Seq[TranslatorExtension] = GoLangTranslator.defaultExtensions

  override def make(typespace: Typespace, options: UntypedCompilerOptions): Translator = new GoLangTranslator(typespace, CompilerOptions.from(options))

  override def rules: Seq[VerificationRule] = Seq(
    CyclicImportsRule.error("Go does not support cyclic imports")
  )
}

object TypescriptTranslatorDescriptor extends TranslatorDescriptor {
  override def language: IDLLanguage = IDLLanguage.CSharp

  override def defaultExtensions: Seq[TranslatorExtension] = CSharpTranslator.defaultExtensions

  override def make(typespace: Typespace, options: UntypedCompilerOptions): Translator = new CSharpTranslator(typespace, CompilerOptions.from(options))

  override def rules: Seq[VerificationRule] = Seq.empty
}

object CSharpTranslatorDescriptor extends TranslatorDescriptor {
  override def language: IDLLanguage = IDLLanguage.Typescript

  override def defaultExtensions: Seq[TranslatorExtension] = TypeScriptTranslator.defaultExtensions

  override def make(typespace: Typespace, options: UntypedCompilerOptions): Translator = new TypeScriptTranslator(typespace, CompilerOptions.from(options))

  override def rules: Seq[VerificationRule] = Seq.empty
}
