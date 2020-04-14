package izumi.fundamentals.platform.files

import java.io.{File, IOException}
import java.net.URI
import java.nio.charset.StandardCharsets
import java.nio.file._
import java.nio.file.attribute.BasicFileAttributes
import java.time.LocalDateTime
import java.util.stream.Collectors

import izumi.fundamentals.platform.language.Quirks
import izumi.fundamentals.platform.os.{IzOs, OsType}
import izumi.fundamentals.platform.time.IzTime

import scala.jdk.CollectionConverters._
import scala.util.Try

object IzFiles {
  def homedir(): String = {
    Paths.get(System.getProperty("user.home")).toFile.getCanonicalPath
  }

  def getFs(uri: URI): Try[FileSystem] = synchronized {
    Try(FileSystems.getFileSystem(uri))
      .recover {
        case _ =>
          FileSystems.newFileSystem(uri, Map.empty[String, Any].asJava)
      }
  }

  def getLastModified(directory: File): Option[LocalDateTime] = {
    import IzTime._

    if (!directory.exists()) {
      return None
    }

    if (directory.isDirectory) {
      val dmt = directory.lastModified().asEpochMillisLocal

      val fmt = walk(directory).map(_.toFile.lastModified().asEpochMillisLocal)

      Some((dmt +: fmt).max)
    } else {
      Some(directory.lastModified().asEpochMillisLocal)
    }
  }

  def walk(directory: File): Seq[Path] = {
    Files.walk(directory.toPath).collect(Collectors.toList()).asScala.toSeq
  }

  def recreateDirs(paths: Path*): Unit = {
    paths.foreach(recreateDir)
  }

  def readString(path: Path): String = {
    import java.nio.file.Files
    new String(Files.readAllBytes(path), StandardCharsets.UTF_8)
  }

  def readString(file: File): String = {
    readString(file.toPath)
  }

  def recreateDir(path: Path): Unit = {
    val asFile = path.toFile

    if (asFile.exists()) {
      removeDir(path)
    }

    Quirks.discard(asFile.mkdirs())
  }

  def removeDir(root: Path): Unit = {
    val _ = Files.walkFileTree(root, new SimpleFileVisitor[Path] {
      override def visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult = {
        Files.delete(file)
        FileVisitResult.CONTINUE
      }

      override def postVisitDirectory(dir: Path, exc: IOException): FileVisitResult = {
        Files.delete(dir)
        FileVisitResult.CONTINUE
      }

    })
  }

  def refreshSymlink(symlink: Path, target: Path): Unit = {
    Quirks.discard(symlink.toFile.delete())
    Quirks.discard(Files.createSymbolicLink(symlink, target.toFile.getCanonicalFile.toPath))
  }

  def find(candidates: Seq[String], paths: Seq[String]): Option[Path] = {
    paths
      .view
      .flatMap {
        p =>
          candidates.map(ext => Paths.get(p).resolve(ext))
      }
      .find {
        p =>
          p.toFile.exists()
      }
  }

  def haveExecutables(names: String*): Boolean = {
    names.forall(which(_).nonEmpty)
  }

  def which(name: String, morePaths: Seq[String] = Seq.empty): Option[Path] = {
    val candidates = IzOs.osType match {
      case OsType.Windows =>
        Seq("exe", "com", "bat").map(ext => s"$name.$ext")
      case _ =>
        Seq(name)
    }

    find(candidates, IzOs.path ++ morePaths)
  }
}
