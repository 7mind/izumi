package com.github.pshirshov.izumi.idealingua.translator

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}

import com.github.pshirshov.izumi.idealingua.model.typespace.Typespace
import com.github.pshirshov.izumi.idealingua.translator.TypespaceCompiler.{CompilerOptions, IDLResult}
import com.github.pshirshov.izumi.idealingua.translator.tocsharp.FinalTranslatorCSharpImpl
import com.github.pshirshov.izumi.idealingua.translator.togolang.FinalTranslatorGoLangImpl
import com.github.pshirshov.izumi.idealingua.translator.toscala.FinalTranslatorScalaImpl
import com.github.pshirshov.izumi.idealingua.translator.totypescript.FinalTranslatorTypeScriptImpl


class TypespaceCompiler(typespace: Typespace) {
  def compile(target: Path, options: CompilerOptions): IDLResult = {
    val translator = toTranslator(options)
    val modules = translator.translate(typespace, options.extensions)

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

  private def toTranslator(options: CompilerOptions): FinalTranslator = {
    options.language match {
      case IDLLanguage.Scala =>
        new FinalTranslatorScalaImpl()
      case IDLLanguage.Go =>
        new FinalTranslatorGoLangImpl()
      case IDLLanguage.Typescript =>
        new FinalTranslatorTypeScriptImpl()
      case IDLLanguage.CSharp =>
        new FinalTranslatorCSharpImpl()
    }
  }
}

object TypespaceCompiler {

  final case class CompilerOptions(
                                    language: IDLLanguage
                                    , extensions: Seq[TranslatorExtension]
                                    , withRuntime: Boolean = true
                                  )

  trait IDLResult

  final case class IDLSuccess(target: Path, paths: Seq[Path]) extends IDLResult

  final case class IDLFailure() extends IDLResult

}
