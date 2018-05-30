package com.github.pshirshov.izumi.idealingua.translator

import java.nio.file.Path

import com.github.pshirshov.izumi.fundamentals.platform.files.IzFiles
import com.github.pshirshov.izumi.fundamentals.platform.resources.IzResources
import com.github.pshirshov.izumi.idealingua.model.common.DomainId
import com.github.pshirshov.izumi.idealingua.model.typespace.Typespace
import com.github.pshirshov.izumi.idealingua.translator.TypespaceCompiler.{CompilerOptions, IDLResult}

class IDLCompiler(toCompile: Seq[Typespace]) {
  def compile(target: Path, options: CompilerOptions): IDLCompiler.Result = {
    IzFiles.recreateDir(target)

    val result = toCompile.map {
      typespace =>

        typespace.domain.id -> invokeCompiler(target, options, typespace)
    }

    val stubs = if (options.withRuntime) {
      IzResources.copyFromJar(s"runtime/${options.language.toString}", target)
    } else {
      IzResources.RecursiveCopyOutput(0)
    }

    val ztarget = target.getParent.resolve(s"${options.language.toString}.zip")
    zip(ztarget, enumerate(target))

    IDLCompiler.Result(result.toMap, stubs, ztarget)
  }

  protected def invokeCompiler(target: Path, options: CompilerOptions, typespace: Typespace): IDLResult = {
    val compiler = new TypespaceCompiler(typespace)
    compiler.compile(target, options)
  }

  import java.nio.file.{Files, Path}

  import scala.collection.JavaConverters._

  private def enumerate(src: Path): List[ZE] = {
    val files = Files.walk(src).iterator()
      .asScala
      .filter(_.toFile.isFile).toList

    files.map(f => {
      ZE(src.relativize(f).toString, f)
    })
  }

  final case class ZE(name: String, file: Path)

  def zip(out: Path, files: Iterable[ZE]): Unit = {
    import java.io.{BufferedInputStream, FileInputStream, FileOutputStream}
    import java.util.zip.{ZipEntry, ZipOutputStream}

    val outFile = out.toFile
    if (outFile.exists()) {
      outFile.delete()
    }

    val zip = new ZipOutputStream(new FileOutputStream(outFile))

    files.foreach { name =>
      zip.putNextEntry(new ZipEntry(name.name))
      val in = new BufferedInputStream(new FileInputStream(name.file.toFile))
      var b = in.read()
      while (b > -1) {
        zip.write(b)
        b = in.read()
      }
      in.close()
      zip.closeEntry()
    }
    zip.close()
  }
}

object IDLCompiler {

  case class Result(
                     invokation: Map[DomainId, IDLResult]
                     , stubs: IzResources.RecursiveCopyOutput
                     , sources: Path
                   )



}
