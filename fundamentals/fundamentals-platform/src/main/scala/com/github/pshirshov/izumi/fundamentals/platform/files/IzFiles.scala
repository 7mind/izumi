package com.github.pshirshov.izumi.fundamentals.platform.files

import java.io.IOException
import java.nio.file.{FileVisitResult, Files, Path, SimpleFileVisitor}
import java.nio.file.attribute.BasicFileAttributes

import com.github.pshirshov.izumi.fundamentals.platform.language.Quirks

object IzFiles {
  def recreateDirs(paths: Path*): Unit = {
    paths.foreach(recreateDir)
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
}
