package izumi.idealingua.il.loader

import java.io.File
import java.nio.file.{Files, Path}

import izumi.fundamentals.platform.files.{IzFiles, IzZip}
import izumi.idealingua.model.loader.FSPath

class LocalFilesystemEnumerator(roots: Seq[Path], cp: Seq[File], expectedExtensions: Set[String]) extends FilesystemEnumerator {
  def enumerate(): Map[FSPath, String] = {
    val loaded = (roots ++ cp.map(_.toPath))
      .filter(_.toFile.exists())
      .flatMap {
        dir =>
          val file = dir.toFile
          if (file.isDirectory) {
            enumerateDirectory(dir)
          } else if (file.isFile) {
            enumerateZip(dir)
          } else {
            Seq.empty
          }

      }
      .toMap

//    import IzString._
//    println(s"Loaded: ${loaded.keys.niceList()}")

    loaded
  }

  def toFsPath(path: Path): FSPath = {
    FSPath.parse(path.toString)
  }

  def enumerateZip(directory: Path): Seq[(FSPath, String)] = {
    IzZip.findInZips(Seq(directory.toFile), hasExpectedExt)
      .map {
        case (path, content) =>
          toFsPath(path) -> content
      }
      .toSeq
  }

  def enumerateDirectory(directory: Path): Seq[(FSPath, String)] = {
    import scala.jdk.CollectionConverters._

    java.nio.file.Files.walk(directory)
      .iterator().asScala
      .filter {
        p => Files.isRegularFile(p) && hasExpectedExt(p)
      }
      .map(f => toFsPath(directory.relativize(f)) -> IzFiles.readString(f))
      .toSeq
  }

  private def hasExpectedExt(path: Path): Boolean = {
    expectedExtensions.exists(ext => Option(path.getFileName).exists(_.toString.endsWith(ext)))
  }
}
