package com.github.pshirshov.izumi.idealingua.translator

import com.github.pshirshov.izumi.idealingua.model.output.Module
import com.github.pshirshov.izumi.idealingua.model.publishing.BuildManifest
import com.github.pshirshov.izumi.idealingua.model.publishing.manifests.{CSharpBuildManifest, GoLangBuildManifest, ScalaBuildManifest, TypeScriptBuildManifest}
import com.github.pshirshov.izumi.idealingua.translator.tocsharp.extensions.CSharpTranslatorExtension
import com.github.pshirshov.izumi.idealingua.translator.togolang.extensions.GoLangTranslatorExtension
import com.github.pshirshov.izumi.idealingua.translator.toscala.extensions.ScalaTranslatorExtension
import com.github.pshirshov.izumi.idealingua.translator.totypescript.extensions.TypeScriptTranslatorExtension

import scala.reflect.ClassTag

case class ProvidedRuntime(modules: Seq[Module])

sealed trait AbstractCompilerOptions[E <: TranslatorExtension, M <: BuildManifest] {
  def language: IDLLanguage

  def extensions: Seq[E]

  def withRuntime: Boolean

  def manifest: Option[M]

  def providedRuntime: Option[ProvidedRuntime]
}


final case class CompilerOptions[E <: TranslatorExtension, M <: BuildManifest]
(
  language: IDLLanguage
  , extensions: Seq[E]
  , withRuntime: Boolean = true
  , manifest: Option[M] = None
  , providedRuntime: Option[ProvidedRuntime] = None
) extends AbstractCompilerOptions[E, M]

object CompilerOptions {
  type TypescriptTranslatorOptions = CompilerOptions[TypeScriptTranslatorExtension, TypeScriptBuildManifest]
  type GoTranslatorOptions = CompilerOptions[GoLangTranslatorExtension, GoLangBuildManifest]
  type CSharpTranslatorOptions = CompilerOptions[CSharpTranslatorExtension, CSharpBuildManifest]
  type ScalaTranslatorOptions = CompilerOptions[ScalaTranslatorExtension, ScalaBuildManifest]

  def from[E <: TranslatorExtension : ClassTag, M <: BuildManifest : ClassTag](options: UntypedCompilerOptions): CompilerOptions[E, M] = {
    val extensions = options.extensions.collect {
      case e: E => e
    }

    val manifest = options.manifest.collect {
      case m: M => m
    }

    CompilerOptions(options.language, extensions, options.withRuntime, manifest, options.providedRuntime)
  }
}

final case class UntypedCompilerOptions
(
  language: IDLLanguage
  , extensions: Seq[TranslatorExtension]
  , withRuntime: Boolean = true
  , manifest: Option[BuildManifest] = None
  , providedRuntime: Option[ProvidedRuntime] = None
) extends AbstractCompilerOptions[TranslatorExtension, BuildManifest] {
  override def toString: String = {
    val rtRepr = Option(withRuntime).filter(_ == true).map(_ => "+rt").getOrElse("-rt")
    val mfRepr = manifest.map(_ => "+mf").getOrElse("-mf")
    val extRepr = extensions.mkString("(", ", ", ")")
    val rtfRepr = providedRuntime.map(rt => s"rtf=${rt.modules.size}").getOrElse("-rtf")
    Seq(language, mfRepr, extRepr, rtRepr, rtfRepr).mkString(" ")
  }
}






