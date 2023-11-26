package izumi.fundamentals.platform.files

import izumi.fundamentals.platform.language.Quirks

import java.nio.file.{Files, Path}

trait FsRefresh { this: RecursiveFileRemovals =>
  def recreateDirs(paths: Path*): Unit = {
    paths.foreach(recreateDir)
  }

  def recreateDir(path: Path): Unit = {
    val asFile = path.toFile

    if (asFile.exists()) {
      erase(path)
    }

    Quirks.discard(asFile.mkdirs())
  }

  def refreshSymlink(symlink: Path, target: Path): Unit = {
    Quirks.discard(symlink.toFile.delete())
    Quirks.discard(Files.createSymbolicLink(symlink, target.toFile.getCanonicalFile.toPath))
  }
}
