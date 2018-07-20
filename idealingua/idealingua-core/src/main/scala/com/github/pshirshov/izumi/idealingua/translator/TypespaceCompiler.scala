package com.github.pshirshov.izumi.idealingua.translator

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}

import com.github.pshirshov.izumi.idealingua.model.output.Module
import com.github.pshirshov.izumi.idealingua.model.publishing.BuildManifest
import com.github.pshirshov.izumi.idealingua.model.typespace.Typespace
import com.github.pshirshov.izumi.idealingua.translator.TypespaceCompiler.{AbstractCompilerOptions, CompilerOptions, UntypedCompilerOptions, IDLResult}
import com.github.pshirshov.izumi.idealingua.translator.tocsharp.CSharpTranslator
import com.github.pshirshov.izumi.idealingua.translator.togolang.GoLangTranslator
import com.github.pshirshov.izumi.idealingua.translator.toscala.ScalaTranslator
import com.github.pshirshov.izumi.idealingua.translator.totypescript.TypeScriptTranslator

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

  protected def convert[E <: TranslatorExtension : ClassTag, M <: BuildManifest : ClassTag](options: UntypedCompilerOptions): AbstractCompilerOptions[E, M] = {
    val extensions = options.extensions.collect {
      case e: E => e
    }

    val manifest = options.manifest.collect {
      case m: M => m
    }

    CompilerOptions(options.language, extensions, options.withRuntime, manifest)
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

object TypespaceCompiler {

  trait AbstractCompilerOptions[E <: TranslatorExtension, M <: BuildManifest] {
    def language: IDLLanguage
    def extensions: Seq[E]
    def withRuntime: Boolean
    def manifest: Option[M]

  }

  final case class CompilerOptions[E <: TranslatorExtension, M <: BuildManifest](
                                    language: IDLLanguage
                                    , extensions: Seq[E]
                                    , withRuntime: Boolean = true
                                    , manifest: Option[M] = None
                                  ) extends AbstractCompilerOptions[E, M]


  final case class UntypedCompilerOptions(
                                    language: IDLLanguage
                                    , extensions: Seq[TranslatorExtension]
                                    , withRuntime: Boolean = true
                                    , manifest: Option[BuildManifest] = None
                                  ) extends AbstractCompilerOptions[TranslatorExtension, BuildManifest]


  trait IDLResult

  final case class IDLSuccess(target: Path, paths: Seq[Path]) extends IDLResult

  final case class IDLFailure() extends IDLResult

}
