package izumi.fundamentals.platform.files

import java.io.{File, IOException}
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.{FileVisitResult, Files, Path, SimpleFileVisitor}

trait RecursiveFileRemovals {
  def erase(root: Path): Unit = {
    val _ = Files.walkFileTree(
      root,
      new SimpleFileVisitor[Path] {
        override def visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult = {
          Files.delete(file)
          FileVisitResult.CONTINUE
        }

        override def postVisitDirectory(dir: Path, exc: IOException): FileVisitResult = {
          Files.delete(dir)
          FileVisitResult.CONTINUE
        }

      },
    )
  }

  def erase(root: File): Unit = {
    erase(root.toPath)
  }

  @deprecated("use IzFiles.erase")
  def removeDir(root: Path): Unit = {
    erase(root)
  }
}
