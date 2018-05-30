package com.github.pshirshov.izumi.idealingua.translator

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}

import com.github.pshirshov.izumi.fundamentals.platform.resources.IzResources
import com.github.pshirshov.izumi.fundamentals.platform.resources.IzResources.RecursiveCopyOutput
import com.github.pshirshov.izumi.idealingua.model.typespace.Typespace
import com.github.pshirshov.izumi.idealingua.translator.IDLCompiler.{CompilerOptions, IDLResult}
import com.github.pshirshov.izumi.idealingua.translator.togolang.FinalTranslatorGoLangImpl
import com.github.pshirshov.izumi.idealingua.translator.toscala.FinalTranslatorScalaImpl
import com.github.pshirshov.izumi.idealingua.translator.totypescript.FinalTranslatorTypeScriptImpl


class IDLCompiler(typespace: Typespace) {
  def compile(target: Path, options: CompilerOptions): IDLResult = {
    val translator = toTranslator(options)
    val modules = translator.translate(typespace, options.extensions)

    val stubs = if (options.withRuntime) {
      IzResources.copyFromJar(s"runtime/${options.language.toString}", target)
    } else {
      IzResources.RecursiveCopyOutput(0)
    }

    val files = modules.map {
      module =>
        val parts = module.id.path :+ module.id.name
        val modulePath = parts.foldLeft(target) { case (path, part) => path.resolve(part) }
        modulePath.getParent.toFile.mkdirs()
        Files.write(modulePath, module.content.getBytes(StandardCharsets.UTF_8))
        modulePath
    }
    IDLCompiler.IDLSuccess(files, stubs)
  }

  private def toTranslator(options: CompilerOptions): FinalTranslator = {
    options.language match {
      case IDLLanguage.Scala =>
        new FinalTranslatorScalaImpl()
      case IDLLanguage.Go =>
        new FinalTranslatorGoLangImpl()
      case IDLLanguage.Typescript =>
        new FinalTranslatorTypeScriptImpl()
      case IDLLanguage.UnityCSharp =>
        ??? // c# transpiler is not ready
    }
  }
}

object IDLCompiler {

  final case class CompilerOptions(
                                    language: IDLLanguage
                                    , extensions: Seq[TranslatorExtension]
                                    , withRuntime: Boolean = true
                                  )

  trait IDLResult

  final case class IDLSuccess(paths: Seq[Path], stubs: RecursiveCopyOutput) extends IDLResult

  final case class IDLFailure() extends IDLResult

}
