package com.github.pshirshov.izumi.idealingua.translator

import com.github.pshirshov.izumi.idealingua.model.typespace.Typespace
import com.github.pshirshov.izumi.idealingua.model.typespace.verification.VerificationRule
import com.github.pshirshov.izumi.idealingua.model.typespace.verification.rules.CyclicImportsRule
import com.github.pshirshov.izumi.idealingua.translator.CompilerOptions.{CSharpTranslatorOptions, GoTranslatorOptions, ScalaTranslatorOptions, TypescriptTranslatorOptions}
import com.github.pshirshov.izumi.idealingua.translator.tocsharp.{CSharpLayouter, CSharpTranslator}
import com.github.pshirshov.izumi.idealingua.translator.togolang.{GoLayouter, GoLangTranslator}
import com.github.pshirshov.izumi.idealingua.translator.toscala.{ScalaLayouter, ScalaTranslator}
import com.github.pshirshov.izumi.idealingua.translator.totypescript.{TypeScriptTranslator, TypescriptLayouter}

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


object ScalaTranslatorDescriptor extends TranslatorDescriptor[ScalaTranslatorOptions] {

  override def typedOptions(options: UntypedCompilerOptions): ScalaTranslatorOptions = CompilerOptions.from(options)

  override def language: IDLLanguage = IDLLanguage.Scala

  override def defaultExtensions: Seq[TranslatorExtension] = ScalaTranslator.defaultExtensions

  override def make(typespace: Typespace, options: UntypedCompilerOptions): Translator = new ScalaTranslator(typespace, typedOptions(options))

  override def rules: Seq[VerificationRule] = Seq.empty

  override def makeHook(options: UntypedCompilerOptions): TranslationLayouter = new ScalaLayouter(typedOptions(options))
}

object GoTranslatorDescriptor extends TranslatorDescriptor[GoTranslatorOptions] {

  override def typedOptions(options: UntypedCompilerOptions): GoTranslatorOptions = CompilerOptions.from(options)

  override def language: IDLLanguage = IDLLanguage.Go

  override def defaultExtensions: Seq[TranslatorExtension] = GoLangTranslator.defaultExtensions

  override def make(typespace: Typespace, options: UntypedCompilerOptions): Translator = new GoLangTranslator(typespace, typedOptions(options))

  override def rules: Seq[VerificationRule] = Seq(
    CyclicImportsRule.error("Go does not support cyclic imports")
  )

  override def makeHook(options: UntypedCompilerOptions): TranslationLayouter = new GoLayouter(typedOptions(options))
}

object CSharpTranslatorDescriptor extends TranslatorDescriptor[CSharpTranslatorOptions] {

  override def typedOptions(options: UntypedCompilerOptions): CSharpTranslatorOptions = CompilerOptions.from(options)

  override def language: IDLLanguage = IDLLanguage.CSharp

  override def defaultExtensions: Seq[TranslatorExtension] = CSharpTranslator.defaultExtensions

  override def make(typespace: Typespace, options: UntypedCompilerOptions): Translator = new CSharpTranslator(typespace, typedOptions(options))

  override def rules: Seq[VerificationRule] = Seq.empty

  override def makeHook(options: UntypedCompilerOptions): TranslationLayouter = new CSharpLayouter(typedOptions(options))
}

object TypescriptTranslatorDescriptor extends TranslatorDescriptor[TypescriptTranslatorOptions] {

  override def typedOptions(options: UntypedCompilerOptions): TypescriptTranslatorOptions = CompilerOptions.from(options)

  override def language: IDLLanguage = IDLLanguage.Typescript

  override def defaultExtensions: Seq[TranslatorExtension] = TypeScriptTranslator.defaultExtensions

  override def make(typespace: Typespace, options: UntypedCompilerOptions): Translator = new TypeScriptTranslator(typespace, typedOptions(options))

  override def rules: Seq[VerificationRule] = Seq.empty

  override def makeHook(options: UntypedCompilerOptions): TranslationLayouter = new TypescriptLayouter(typedOptions(options))
}
