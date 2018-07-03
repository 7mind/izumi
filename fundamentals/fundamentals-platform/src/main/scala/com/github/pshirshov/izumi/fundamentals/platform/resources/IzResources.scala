package com.github.pshirshov.izumi.fundamentals.platform.resources

import java.nio.file._
import java.nio.file.attribute.BasicFileAttributes
import java.io.{BufferedReader, InputStream, InputStreamReader}
import java.util.stream.Collectors

import scala.collection.mutable


object IzResources {

  class PathReference(val path: Path, val fileSystem: FileSystem) extends AutoCloseable {
    override def close(): Unit = {
      if (this.fileSystem != null) this.fileSystem.close()
    }
  }

  def getPath(resPath: String): Option[PathReference] = {
    if (Paths.get(resPath).toFile.exists()) {
      return Some(new PathReference(Paths.get(resPath), null))
    }

    val u = getClass.getClassLoader.getResource(resPath)
    if (u == null) {
      return None
    }

    try {
      Some(new PathReference(Paths.get(u.toURI), null))
    } catch {
      case _: FileSystemNotFoundException =>
        val env: Map[String, _] = Map.empty
        import scala.collection.JavaConverters._

        val fs: FileSystem = FileSystems.newFileSystem(u.toURI, env.asJava)
        Some(new PathReference(fs.provider().getPath(u.toURI), fs))
    }
  }

  case class RecursiveCopyOutput(files: Seq[Path])

  object RecursiveCopyOutput {
    def empty: RecursiveCopyOutput = RecursiveCopyOutput(Seq.empty)
  }

  def copyFromClasspath(sourcePath: String, targetDir: Path): RecursiveCopyOutput = {
    val pathReference = getPath(sourcePath)
    if (pathReference.isEmpty) {
      return RecursiveCopyOutput.empty
    }

    val jarPath: Path = pathReference.get.path
    println(sourcePath)
    println(jarPath)
    val targets = mutable.ArrayBuffer.empty[Path]
    Files.walkFileTree(
      jarPath,
      new SimpleFileVisitor[Path]() {
        private var currentTarget: Path = _

        override def preVisitDirectory(
                                        dir: Path,
                                        attrs: BasicFileAttributes): FileVisitResult = {
          currentTarget = targetDir.resolve(jarPath.relativize(dir).toString)
          Files.createDirectories(currentTarget)
          FileVisitResult.CONTINUE
        }

        override def visitFile(file: Path,
                               attrs: BasicFileAttributes): FileVisitResult = {
          val target = targetDir.resolve(jarPath.relativize(file).toString)
          targets += target
          Files.copy(
            file
            , target
            , StandardCopyOption.REPLACE_EXISTING
          )
          FileVisitResult.CONTINUE
        }
      }
    )

    RecursiveCopyOutput(targets)
  }


  def read(fileName: String): Option[InputStream] = {
    Option(getClass.getClassLoader.getResourceAsStream(fileName))
  }

  def readAsString(fileName: String): Option[String] = {
    read(fileName).map {
      is =>
        val reader = new BufferedReader(new InputStreamReader(is))
        reader.lines.collect(Collectors.joining(System.lineSeparator))
    }
  }
}
