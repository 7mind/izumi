package com.github.pshirshov.izumi.idealingua.translator

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}

import com.github.pshirshov.izumi.idealingua.model.exceptions.IDLException
import com.github.pshirshov.izumi.idealingua.model.output.Module
import com.github.pshirshov.izumi.idealingua.model.publishing.BuildManifest
import com.github.pshirshov.izumi.idealingua.model.publishing.manifests.{CSharpBuildManifest, GoLangBuildManifest, ScalaBuildManifest, TypeScriptBuildManifest}
import com.github.pshirshov.izumi.idealingua.model.typespace.Typespace
import com.github.pshirshov.izumi.idealingua.translator.TypespaceCompiler.{CompilerOptions, IDLResult, UntypedCompilerOptions}
import com.github.pshirshov.izumi.idealingua.translator.tocsharp.CSharpTranslator
import com.github.pshirshov.izumi.idealingua.translator.tocsharp.extensions.CSharpTranslatorExtension
import com.github.pshirshov.izumi.idealingua.translator.togolang.GoLangTranslator
import com.github.pshirshov.izumi.idealingua.translator.togolang.extensions.GoLangTranslatorExtension
import com.github.pshirshov.izumi.idealingua.translator.toscala.ScalaTranslator
import com.github.pshirshov.izumi.idealingua.translator.toscala.extensions.ScalaTranslatorExtension
import com.github.pshirshov.izumi.idealingua.translator.totypescript.TypeScriptTranslator
import com.github.pshirshov.izumi.idealingua.translator.totypescript.extensions.TypeScriptTranslatorExtension

import scala.reflect._


class TypespaceCompiler(typespace: Typespace) {
  def compile(target: Path, options: UntypedCompilerOptions): IDLResult = {
    val modules = translate(typespace, options)

    val files = modules.map {
      module =>
        val parts = module.id.path :+ module.id.name
        val modulePath = parts.foldLeft(target) { case (path, part) => path.resolve(part) }
        modulePath.getParent.toFile.mkdirs()
        Files.write(modulePath, module.content.getBytes(StandardCharsets.UTF_8))
        modulePath
    }

    TypespaceCompiler.IDLSuccess(target, files)
  }

  protected def convert[E <: TranslatorExtension : ClassTag, M <: BuildManifest : ClassTag](options: UntypedCompilerOptions): CompilerOptions[E, M] = {
    val extensions = options.extensions.collect {
      case e: E => e
    }

    val manifest = options.manifest.collect {
      case m: M => m
    }

    CompilerOptions(options.language, extensions, options.withRuntime, manifest, options.providedRuntime)
  }

  private def translate(typespace: Typespace, options: UntypedCompilerOptions): Seq[Module] = {
    options.language match {
      case IDLLanguage.Scala =>
        new ScalaTranslator(typespace, convert(options)).translate()
      case IDLLanguage.Go =>
        new GoLangTranslator(typespace, convert(options)).translate()
      case IDLLanguage.Typescript =>
        new TypeScriptTranslator(typespace, convert(options)).translate()
      case IDLLanguage.CSharp =>
        new CSharpTranslator(typespace, convert(options)).translate()
    }
  }
}

trait Translator {
  def translate(): Seq[Module]

  protected def addRuntime(options: CompilerOptions[_, _], generated: Seq[Module]): Seq[Module] = {
    mergeModules(generated, options.providedRuntime.toSeq.flatMap(_.modules).toSeq)
  }

  protected def mergeModules(m1: Seq[Module], m2: Seq[Module]): Seq[Module] = {
    val combined = m1 ++ m2

    import com.github.pshirshov.izumi.fundamentals.platform.strings.IzString._
    val grouped = combined.groupBy(_.id)

    val conflicts = grouped.filter(_._2.size > 1)
    if (conflicts.nonEmpty) {
      throw new IDLException(s"Conflicting modules: ${conflicts.map(kv => s"${kv._1} => ${kv._2.map(_.id).mkString(", ")}").niceList()}")
    }

    combined
  }
}

object TypespaceCompiler {

  case class ProvidedRuntime(modules: Seq[Module])

  trait AbstractCompilerOptions[E <: TranslatorExtension, M <: BuildManifest] {
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


  type TypescriptTranslatorOptions = CompilerOptions[TypeScriptTranslatorExtension, TypeScriptBuildManifest]
  type GoTranslatorOptions = CompilerOptions[GoLangTranslatorExtension, GoLangBuildManifest]
  type CSharpTranslatorOptions = CompilerOptions[CSharpTranslatorExtension, CSharpBuildManifest]
  type ScalaTranslatorOptions = CompilerOptions[ScalaTranslatorExtension, ScalaBuildManifest]

  trait IDLResult

  final case class IDLSuccess(target: Path, paths: Seq[Path]) extends IDLResult

  final case class IDLFailure() extends IDLResult

}
