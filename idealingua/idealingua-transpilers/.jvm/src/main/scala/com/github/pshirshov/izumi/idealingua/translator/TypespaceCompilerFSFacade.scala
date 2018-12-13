package com.github.pshirshov.izumi.idealingua.translator

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}

import com.github.pshirshov.izumi.fundamentals.platform.files.{IzFiles, IzZip}
import com.github.pshirshov.izumi.fundamentals.platform.resources.IzResources
import com.github.pshirshov.izumi.idealingua.model.common.DomainId
import com.github.pshirshov.izumi.idealingua.model.problems.IDLException
import com.github.pshirshov.izumi.idealingua.model.loader.LoadedDomain
import com.github.pshirshov.izumi.idealingua.model.output.{Module, ModuleId}
import com.github.pshirshov.izumi.idealingua.model.typespace.Typespace

class TypespaceCompilerFSFacade(toCompile: Seq[LoadedDomain.Success]) {
  def compile(relTarget: Path, options: UntypedCompilerOptions): TypespaceCompilerFSFacade.Result = {
    val target = relTarget.toAbsolutePath
    IzFiles.recreateDir(target)

    val withRt = if (options.withRuntime) {
      val iterator = IzResources.enumerateClasspath(s"runtime/${options.language.toString}")
      val rtFiles = iterator.files.map {
        f =>
          import scala.collection.JavaConverters._
          val parts = f.path.iterator().asScala.toList.map(_.toString)
          Module(ModuleId(parts.init, parts.last), new String(f.content, StandardCharsets.UTF_8))
      }.toList // .toList is important here, iterable is mutable
      options.copy(providedRuntime = Some(ProvidedRuntime(rtFiles)))
    } else {
      options
    }

    val result = toCompile.map {
      loaded =>
        loaded.typespace.domain.id -> invokeCompiler(target, withRt, loaded.typespace)
    }

    val success = result.collect({ case (id, success: IDLCompilationResult.Success) => id -> success })
    val failure = result.collect({ case (id, failure: IDLCompilationResult.Failure) => s"$id: $failure" })

    if (failure.nonEmpty) {
      throw new IllegalStateException(s"Cannot compile models: ${failure.mkString("\n  ")}")
    }

    // pack output
    import IzZip._

    val ztarget = target
      .getParent
      .resolve(s"${options.language.toString}.zip")

    val toPack = success.flatMap {
      case (_, s) =>
        s.paths.map(p => ZE(s.target.relativize(p).toString, p))
    }

    val grouped = toPack.groupBy(_.name)

    // this check is kinda excessive because we have per-domain conflict checks
    // TODO: this check is kinda expensive as well
    val conflicts = grouped
      .filter { // at first we compare zip entries by file path and entry name
        case (_, v) =>
          v.toSet.size > 1
      }
      .filter { // when compare the rest by content
        case (_, v) =>
          v.map(f => IzFiles.readString(f.file)).toSet.size > 1
      }
      .values

    import com.github.pshirshov.izumi.fundamentals.platform.strings.IzString._
    if (conflicts.nonEmpty) {
      throw new IDLException(s"Cannot continue: conflicting files: ${conflicts.niceList()}")
    }

    zip(ztarget, grouped.values.map(_.head))

    TypespaceCompilerFSFacade.Result(success.toMap, ztarget)
  }

  protected def invokeCompiler(target: Path, options: UntypedCompilerOptions, typespace: Typespace): IDLCompilationResult = {
    val modules = new TypespaceCompilerBaseFacade(typespace, options).compile()

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

object TypespaceCompilerFSFacade {

  case class Result(
                     compilationProducts: Map[DomainId, IDLCompilationResult.Success]
                     , zippedOutput: Path
                   )


}
