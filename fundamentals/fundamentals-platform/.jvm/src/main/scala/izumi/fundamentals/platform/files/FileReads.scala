package izumi.fundamentals.platform.files

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Path

trait FileReads {
  def readString(path: Path): String = {
    import java.nio.file.Files
    new String(Files.readAllBytes(path), StandardCharsets.UTF_8)
  }

  def readString(file: File): String = {
    readString(file.toPath)
  }
}
