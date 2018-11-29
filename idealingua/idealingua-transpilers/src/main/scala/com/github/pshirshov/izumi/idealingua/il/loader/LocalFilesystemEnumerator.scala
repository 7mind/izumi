package com.github.pshirshov.izumi.idealingua.il.loader

import java.nio.file.{Files, Path}

import com.github.pshirshov.izumi.fundamentals.platform.files.IzFiles
import com.github.pshirshov.izumi.idealingua.model.loader.FSPath

class LocalFilesystemEnumerator(root: Path, expectedExtensions: Set[String]) extends FilesystemEnumerator {
  def enumerate(): Map[FSPath, String] = {
    import scala.collection.JavaConverters._

    val file = root.toFile
    if (!file.exists() || !file.isDirectory) {
      return Map.empty
    }

    java.nio.file.Files.walk(root).iterator().asScala
      .filter {
        f => Files.isRegularFile(f) && expectedExtensions.exists(ext => f.getFileName.toString.endsWith(ext))
      }
      .map(f => FSPath(root.relativize(f)) -> IzFiles.readString(f))
      .toMap
  }
}
