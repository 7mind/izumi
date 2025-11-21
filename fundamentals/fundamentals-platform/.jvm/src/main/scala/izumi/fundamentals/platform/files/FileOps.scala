package izumi.fundamentals.platform.files

import izumi.fundamentals.platform.language.Quirks.Discarder

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}

trait FileOps {
  def readString(path: Path): String = {
    import java.nio.file.Files
    new String(Files.readAllBytes(path), StandardCharsets.UTF_8)
  }

  def readString(file: File): String = {
    readString(file.toPath)
  }

  def readString(file: String): String = {
    readString(Paths.get(file))
  }

  def writeUtfString(file: Path, string: String): Unit = {
    Files.write(file, string.getBytes(StandardCharsets.UTF_8)).discard()
  }

  def writeUtfString(file: File, string: String): Unit = {
    writeUtfString(file.toPath, string)
  }

  def writeUtfString(file: String, string: String): Unit = {
    writeUtfString(Paths.get(file), string)
  }

}
