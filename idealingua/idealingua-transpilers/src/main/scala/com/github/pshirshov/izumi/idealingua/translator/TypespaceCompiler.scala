package com.github.pshirshov.izumi.idealingua.translator

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}

import com.github.pshirshov.izumi.idealingua.model.typespace.Typespace


class TypespaceCompiler(typespace: Typespace) {
  def compile(target: Path, options: UntypedCompilerOptions): IDLCompilationResult = {
    val modules = new TypespaceTranslatorFacade(typespace).translate(options)

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


}








