package com.github.pshirshov.izumi.idealingua.translator

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}

import com.github.pshirshov.izumi.idealingua.model.finaldef.DomainDefinition
import com.github.pshirshov.izumi.idealingua.translator.IDLCompiler.IDLResult
import com.github.pshirshov.izumi.idealingua.translator.toscala.FinalTranslatorScalaImpl

class IDLCompiler(domain: DomainDefinition) {
  def compile(target: Path): IDLResult = {
    val modules = new FinalTranslatorScalaImpl().translate(domain)
    val files = modules.map {
      module =>
        val parts = module.id.path :+ module.id.name
        val modulePath = parts.foldLeft(target) { case (path, part) => path.resolve(part) }
        //        val mpath = path.mkString("/")
        //        println(mpath)
        //        println(module.content)
        //        println()
        //        val xpath = Paths.get(path.head, path.tail: _*)
        modulePath.getParent.toFile.mkdirs()
        Files.write(modulePath, module.content.getBytes(StandardCharsets.UTF_8))
        modulePath
    }
    IDLCompiler.IDLSuccess(files)
  }
}

object IDLCompiler {

  trait IDLResult

  case class IDLSuccess(paths: Seq[Path]) extends IDLResult

  case class IDLFailure() extends IDLResult

}
