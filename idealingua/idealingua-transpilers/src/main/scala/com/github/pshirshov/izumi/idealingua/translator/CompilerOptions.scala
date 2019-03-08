package com.github.pshirshov.izumi.idealingua.translator

import com.github.pshirshov.izumi.idealingua.model.output.Module
import com.github.pshirshov.izumi.idealingua.model.publishing.BuildManifest
import com.github.pshirshov.izumi.idealingua.model.publishing.manifests.{CSharpBuildManifest, GoLangBuildManifest, ScalaBuildManifest, TypeScriptBuildManifest}

import scala.reflect.ClassTag

case class ProvidedRuntime(modules: Seq[Module]) {
  def isEmpty: Boolean = modules.isEmpty
  def maybe: Option[ProvidedRuntime] = {
    if (isEmpty) {
      None
    } else {
      Some(this)
    }
  }
  def ++(other: ProvidedRuntime): ProvidedRuntime = {
    ProvidedRuntime(modules ++ other.modules)
  }
}

object ProvidedRuntime {
  def empty: ProvidedRuntime = ProvidedRuntime(Seq.empty)
}

sealed trait AbstractCompilerOptions[E <: TranslatorExtension, M <: BuildManifest] {
  def language: IDLLanguage

  def extensions: Seq[E]

  def withBundledRuntime: Boolean

  def manifest: M

  def providedRuntime: Option[ProvidedRuntime]
}


final case class CompilerOptions[E <: TranslatorExtension, M <: BuildManifest]
(
  language: IDLLanguage
  , extensions: Seq[E]
  , manifest: M
  , withBundledRuntime: Boolean = true
  , providedRuntime: Option[ProvidedRuntime] = None
) extends AbstractCompilerOptions[E, M]

object CompilerOptions {
  type TypescriptTranslatorOptions = CompilerOptions[Nothing, TypeScriptBuildManifest]
  type GoTranslatorOptions = CompilerOptions[Nothing, GoLangBuildManifest]
  type CSharpTranslatorOptions = CompilerOptions[Nothing, CSharpBuildManifest]
  type ScalaTranslatorOptions = CompilerOptions[Nothing, ScalaBuildManifest]

  def from[E <: TranslatorExtension : ClassTag, M <: BuildManifest : ClassTag](options: UntypedCompilerOptions): CompilerOptions[E, M] = {
    val extensions = options.extensions.collect {
      case e: E => e
    }

    val manifest = options.manifest.asInstanceOf[M]

    CompilerOptions(options.language, extensions, manifest, options.withBundledRuntime, options.providedRuntime)
  }
}

final case class UntypedCompilerOptions
(
  language: IDLLanguage
  , extensions: Seq[TranslatorExtension]
  , manifest: BuildManifest
  , withBundledRuntime: Boolean = true
  , providedRuntime: Option[ProvidedRuntime] = None
) extends AbstractCompilerOptions[TranslatorExtension, BuildManifest] {
  override def toString: String = {
    val rtRepr = Option(withBundledRuntime).filter(_ == true).map(_ => "+rtb").getOrElse("-rtb")
    val rtfRepr = providedRuntime.map(rt => s"rtu=${rt.modules.size}").getOrElse("-rtu")
    val extRepr = extensions.mkString("(", ", ", ")")
    Seq(language, rtRepr, rtfRepr, extRepr).mkString(" ")
  }
}






