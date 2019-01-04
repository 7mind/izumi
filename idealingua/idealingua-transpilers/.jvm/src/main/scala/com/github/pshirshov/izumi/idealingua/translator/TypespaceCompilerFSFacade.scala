package com.github.pshirshov.izumi.idealingua.translator

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}

import com.github.pshirshov.izumi.fundamentals.platform.files.{IzFiles, IzZip}
import com.github.pshirshov.izumi.fundamentals.platform.resources.IzResources
import com.github.pshirshov.izumi.idealingua.model.common.DomainId
import com.github.pshirshov.izumi.idealingua.model.loader.LoadedDomain
import com.github.pshirshov.izumi.idealingua.model.output.{Module, ModuleId}
import com.github.pshirshov.izumi.idealingua.model.problems.IDLException


class TypespaceCompilerFSFacade(toCompile: Seq[LoadedDomain.Success]) {

  import TypespaceCompilerFSFacade._

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


    val descriptor = TypespaceCompilerBaseFacade.descriptor(options.language)
    val compiled = toCompile.map {
      loaded =>
        descriptor.make(loaded.typespace, withRt).translate()
    }

    val hook = descriptor.makeHook(options)

    val finalized = hook.finalize(compiled)

    val result = finalized.map {
      out =>
        val files = out.modules.map {
          module =>
            val parts = module.id.path :+ module.id.name
            val modulePath = parts.foldLeft(target) { case (path, part) => path.resolve(part) }
            modulePath.getParent.toFile.mkdirs()
            Files.write(modulePath, module.content.getBytes(StandardCharsets.UTF_8))
            modulePath
        }

        IDLCompilationResult(out.typespace.domain.id, target, files)
    }

    // pack output
    import IzZip._

    val ztarget = target
      .getParent
      .resolve(s"${options.language.toString}.zip")

    val toPack = result.flatMap {
      s =>
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

    TypespaceCompilerFSFacade.Result(result, ztarget)
  }
}

object TypespaceCompilerFSFacade {

  final case class IDLCompilationResult(id: DomainId, target: Path, paths: Seq[Path])

  case class Result(
                     compilationProducts: Seq[IDLCompilationResult]
                     , zippedOutput: Path
                   )


}
