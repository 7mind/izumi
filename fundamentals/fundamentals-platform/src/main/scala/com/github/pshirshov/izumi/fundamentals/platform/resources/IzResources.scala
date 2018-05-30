package com.github.pshirshov.izumi.fundamentals.platform.resources

import java.nio.file._
import java.nio.file.attribute.BasicFileAttributes

import com.github.pshirshov.izumi.fundamentals.platform.language.Quirks._

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
    if ( u == null ) {
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

  def copyFromJar(sourcePath: String, target: Path)(onCopyStartHook: (Path, BasicFileAttributes) => Unit = {(_, _) => }): Unit = {
    val pathReference = getPath(sourcePath)
    if (pathReference.isEmpty) {
      return
    }

    val jarPath: Path = pathReference.get.path
    Files.walkFileTree(
      jarPath,
      new SimpleFileVisitor[Path]() {
        private var currentTarget: Path = _

        override def preVisitDirectory(
                                        dir: Path,
                                        attrs: BasicFileAttributes): FileVisitResult = {
          currentTarget = target.resolve(jarPath.relativize(dir).toString)
          Files.createDirectories(currentTarget)
          FileVisitResult.CONTINUE
        }

        override def visitFile(file: Path,
                               attrs: BasicFileAttributes): FileVisitResult = {
          onCopyStartHook(file, attrs)
          Files.copy(file,
            target.resolve(jarPath.relativize(file).toString),
            StandardCopyOption.REPLACE_EXISTING)
          FileVisitResult.CONTINUE
        }
      }
    ).discard()
  }

}
