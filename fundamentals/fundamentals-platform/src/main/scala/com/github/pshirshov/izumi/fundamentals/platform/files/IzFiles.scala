package com.github.pshirshov.izumi.fundamentals.platform.files

import java.io.{File, IOException}
import java.nio.file._
import java.nio.file.attribute.BasicFileAttributes

import com.github.pshirshov.izumi.fundamentals.platform.language.Quirks
import com.github.pshirshov.izumi.fundamentals.platform.os.{IzOs, OsType}

object IzFiles {
  def recreateDirs(paths: Path*): Unit = {
    paths.foreach(recreateDir)
  }

  def readString(path: Path): String = {
    import java.nio.file.Files
    new String(Files.readAllBytes(path))
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
      .find(p => p.toFile.exists())
  }

  def haveExecutables(names: String*): Boolean = {
    names.forall(which(_).nonEmpty)
  }

  def which(name: String): Option[Path] = {
    val candidates = IzOs.osType match {
      case OsType.Windows =>
        Seq("exe", "com", "bat").map(ext => s"$name.$ext")
      case _ =>
        Seq(name)
    }

    find(candidates, IzOs.path)
  }
}


