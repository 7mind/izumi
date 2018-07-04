package com.github.pshirshov.izumi.idealingua.translator

import java.nio.file.Path

import com.github.pshirshov.izumi.fundamentals.platform.files.{IzFiles, IzZip}
import com.github.pshirshov.izumi.fundamentals.platform.resources.IzResources
import com.github.pshirshov.izumi.idealingua.model.common.DomainId
import com.github.pshirshov.izumi.idealingua.model.typespace.Typespace
import com.github.pshirshov.izumi.idealingua.translator.TypespaceCompiler.{CompilerOptions, IDLFailure, IDLResult, IDLSuccess}

class IDLCompiler(toCompile: Seq[Typespace]) {
  def compile(relTarget: Path, options: CompilerOptions): IDLCompiler.Result = {
    val target = relTarget.toAbsolutePath
    IzFiles.recreateDir(target)

    val result = toCompile.map {
      typespace =>
        typespace.domain.id -> invokeCompiler(target, options, typespace)
    }

    val success = result.collect({ case (id, success: IDLSuccess) => id -> success })
    val failure = result.collect({ case (id, failure: IDLFailure) => s"$id: $failure" })

    if (failure.nonEmpty) {
      throw new IllegalStateException(s"Cannot compile models: ${failure.mkString("\n  ")}")
    }

    // copy stubs
    val stubs = if (options.withRuntime) {
      IzResources.copyFromClasspath(s"runtime/${options.language.toString}", target)
    } else {
      IzResources.RecursiveCopyOutput.empty
    }

    // pack output
    import IzZip._

    val ztarget = target
      .getParent
      .resolve(s"${options.language.toString}.zip")

    val toPack = stubs.files.map {
      stub =>
        ZE(target.relativize(stub).toString, stub)
    } ++ success.flatMap {
      case (_, s) =>
        s.paths.map(p => ZE(s.target.relativize(p).toString, p))
    }
    zip(ztarget, toPack)

    IDLCompiler.Result(success.toMap, stubs, ztarget)
  }

  protected def invokeCompiler(target: Path, options: CompilerOptions, typespace: Typespace): IDLResult = {
    val compiler = new TypespaceCompiler(typespace)
    compiler.compile(target, options)
  }



}

object IDLCompiler {

  case class Result(
                     invokation: Map[DomainId, IDLSuccess]
                     , stubs: IzResources.RecursiveCopyOutput
                     , sources: Path
                   )


}
