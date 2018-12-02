package com.github.pshirshov.izumi.idealingua.translator

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}

import com.github.pshirshov.izumi.idealingua.model.output.Module
import com.github.pshirshov.izumi.idealingua.model.publishing.BuildManifest
import com.github.pshirshov.izumi.idealingua.model.typespace.Typespace
import com.github.pshirshov.izumi.idealingua.translator.tocsharp.CSharpTranslator
import com.github.pshirshov.izumi.idealingua.translator.togolang.GoLangTranslator
import com.github.pshirshov.izumi.idealingua.translator.toscala.ScalaTranslator
import com.github.pshirshov.izumi.idealingua.translator.totypescript.TypeScriptTranslator

import scala.reflect._


class TypespaceCompiler(typespace: Typespace) {
  def compile(target: Path, options: UntypedCompilerOptions): IDLCompilationResult = {
    val modules = translate(typespace, options)

    val files = modules.map {
      module =>
        val parts = module.id.path :+ module.id.name
        val modulePath = parts.foldLeft(target) { case (path, part) => path.resolve(part) }
        modulePath.getParent.toFile.mkdirs()
        Files.write(modulePath, module.content.getBytes(StandardCharsets.UTF_8))
        modulePath
    }

    IDLCompilationResult.Success(target, files)
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

  private def convert[E <: TranslatorExtension : ClassTag, M <: BuildManifest : ClassTag](options: UntypedCompilerOptions): CompilerOptions[E, M] = {
    val extensions = options.extensions.collect {
      case e: E => e
    }

    val manifest = options.manifest.collect {
      case m: M => m
    }

    CompilerOptions(options.language, extensions, options.withRuntime, manifest, options.providedRuntime)
  }
}






