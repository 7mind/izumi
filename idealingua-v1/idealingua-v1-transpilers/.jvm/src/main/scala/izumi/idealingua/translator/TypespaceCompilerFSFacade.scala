package izumi.idealingua.translator

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}

import izumi.fundamentals.platform.files.{IzFiles, IzZip}
import izumi.fundamentals.platform.resources.IzResources
import izumi.idealingua.model.loader.LoadedDomain
import izumi.idealingua.model.output.{Module, ModuleId}
import izumi.idealingua.model.problems.IDLException
import izumi.fundamentals.platform.strings.IzString._

class TypespaceCompilerFSFacade(toCompile: Seq[LoadedDomain.Success]) {

  import TypespaceCompilerFSFacade._

  def compile(relTarget: Path, options: UntypedCompilerOptions): TypespaceCompilerFSFacade.Result = {
    val target = relTarget.toAbsolutePath
    IzFiles.recreateDir(target)

    val loadedRt = loadBundledRuntime(options)
    val fullRt = Seq(options.providedRuntime, loadedRt)
      .map(_.getOrElse(ProvidedRuntime.empty))
      .foldLeft(ProvidedRuntime.empty)(_ ++ _)
      .maybe

    val withRt = options.copy(providedRuntime = fullRt)

    val finalized = new TypespaceCompilerBaseFacade(withRt).compile(toCompile)

    val files = finalized.emodules.map {
      emodule =>
        val module = emodule.module
        val parts = module.id.path :+ module.id.name
        val modulePath = parts.foldLeft(target) { case (path, part) => path.resolve(part) }
        modulePath.getParent.toFile.mkdirs()
        Files.write(modulePath, module.content.utf8)
        modulePath
    }
    val result = IDLCompilationResult(target, files)


    // pack output
    import IzZip._

    val ztarget = target
      .getParent
      .resolve(s"${options.language.toString}.zip")

    val toPack = result.paths.map(p => ZE(result.target.relativize(p).toString, p))
    val grouped = toPack.groupBy(_.name.toLowerCase)
    verifySanity(grouped)
    zip(ztarget, grouped.values.map(_.head))

    TypespaceCompilerFSFacade.Result(result, ztarget)
  }


  private def verifySanity(grouped: Map[String, Seq[IzZip.ZE]]): Unit = {
    val conflicts = grouped
      .filter { // at first we compare zip entries by file path and entry name
        case (_, v) =>
          v.toSet.size > 1
      }
      .filter { // then compare the rest by content
        case (_, v) =>
          v.map(f => IzFiles.readString(f.file)).toSet.size > 1
      }
      .values

    import izumi.fundamentals.platform.strings.IzString._
    if (conflicts.nonEmpty) {
      throw new IDLException(s"Cannot continue: conflicting files: ${conflicts.niceList()}")
    }
  }

  private def loadBundledRuntime(options: UntypedCompilerOptions): Option[ProvidedRuntime] = {
    if (options.withBundledRuntime) {
      val iterator = IzResources.enumerateClasspath(s"runtime/${options.language.toString}")
      val rtFiles = iterator.files.map {
        f =>
          import scala.jdk.CollectionConverters._
          val parts = f.path.iterator().asScala.toList.map(_.toString)
          Module(ModuleId(parts.init, parts.last), new String(f.content, StandardCharsets.UTF_8))
      }.toList // .toList is important here, iterable is mutable

      ProvidedRuntime(rtFiles).maybe
    } else {
      None
    }
  }
}

object TypespaceCompilerFSFacade {

  final case class IDLCompilationResult(target: Path, paths: Seq[Path])

  case class Result(
                     compilationProducts: IDLCompilationResult
                     , zippedOutput: Path
                   )


}
