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

sealed trait AbstractCompilerOptions[M <: BuildManifest] {
  def language: IDLLanguage

  def extensions: ExtensionSpec

  def withBundledRuntime: Boolean

  def manifest: M

  def providedRuntime: Option[ProvidedRuntime]
}


final case class CompilerOptions[M <: BuildManifest]
(
  language: IDLLanguage
  , extensions: ExtensionSpec
  , manifest: M
  , withBundledRuntime: Boolean = true
  , providedRuntime: Option[ProvidedRuntime] = None
) extends AbstractCompilerOptions[M]

object CompilerOptions {
  type TypescriptTranslatorOptions = CompilerOptions[TypeScriptBuildManifest]
  type GoTranslatorOptions = CompilerOptions[GoLangBuildManifest]
  type CSharpTranslatorOptions = CompilerOptions[CSharpBuildManifest]
  type ScalaTranslatorOptions = CompilerOptions[ScalaBuildManifest]

  def from[M <: BuildManifest : ClassTag](options: UntypedCompilerOptions): CompilerOptions[M] = {
    val manifest = options.manifest.asInstanceOf[M]

    CompilerOptions(options.language, options.extensions, manifest, options.withBundledRuntime, options.providedRuntime)
  }
}

sealed trait ExtensionSpec

object ExtensionSpec {
  case object All extends ExtensionSpec {
    override def toString: String = "*"
  }
}

final case class UntypedCompilerOptions
(
  language: IDLLanguage
  , extensions: ExtensionSpec
  , manifest: BuildManifest
  , withBundledRuntime: Boolean
  , providedRuntime: Option[ProvidedRuntime]
) extends AbstractCompilerOptions[BuildManifest] {
  override def toString: String = {
    val rtRepr = Option(withBundledRuntime).filter(_ == true).map(_ => "+rtb").getOrElse("-rtb")
    val rtfRepr = providedRuntime.map(rt => s"rtu=${rt.modules.size}").getOrElse("-rtu")
    val extRepr = extensions.toString
    Seq(language, rtRepr, rtfRepr, extRepr).mkString(" ")
  }
}






