package izumi.fundamentals.platform.files

import java.io.File
import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*

trait FileSearch {
  def walk(directory: Path): Iterator[Path] = {
    Files.walk(directory).iterator().asScala
  }

  def walk(directory: File): Iterator[Path] = {
    walk(directory.toPath)
  }
}
