package izumi.fundamentals.platform.resources

import java.io.{FileSystem => _, _}
import java.net.URL
import java.nio.file._
import java.nio.file.attribute.BasicFileAttributes
import java.util.jar.JarFile
import java.util.stream.Collectors
import java.util.zip.ZipEntry

import izumi.fundamentals.platform.files.IzFiles

import scala.collection.mutable
import scala.language.implicitConversions
import scala.reflect.ClassTag
import scala.util.{Failure, Success}

sealed trait ResourceLocation

// TODO: all this class is a piece of shit
class IzResources(clazz: Class[_]) {

  import IzResources._

  def jarResource[C: ClassTag](name: String): ResourceLocation = {
    classLocationUrl[C]()
      .flatMap {
        url =>
          val location = Paths.get(url.toURI)
          val locFile = location.toFile
          val resolved = location.resolve(name)
          val resolvedFile = resolved.toFile

          if (locFile.exists() && locFile.isFile) { // read from jar
            val jar = new JarFile(locFile)

            Option(jar.getEntry(name)) match {
              case Some(entry) =>
                Some(ResourceLocation.Jar(locFile, jar, entry))
              case None =>
                jar.close()
                None
            }
          } else if (resolvedFile.exists()) {
            Some(ResourceLocation.Filesystem(resolvedFile))
          } else {
            None
          }
      }
      .getOrElse(ResourceLocation.NotFound)
  }

  def classLocationUrl[C: ClassTag](): Option[URL] = {
    import scala.reflect._
    val clazz = classTag[C].runtimeClass
    Option(clazz.getProtectionDomain.getCodeSource.getLocation)
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
        IzFiles.getFs(u.toURI) match {
          case Failure(exception) =>
            throw exception
          case Success(fs) =>
            fs.synchronized {
              Some(new PathReference(fs.provider().getPath(u.toURI), fs))
            }
        }

    }
  }

  def copyFromClasspath(sourcePath: String, targetDir: Path): RecursiveCopyOutput = {
    val pathReference = getPath(sourcePath)
    if (pathReference.isEmpty) {
      return RecursiveCopyOutput.empty
    }

    val jarPath: Path = pathReference.get.path
    val targets = mutable.ArrayBuffer.empty[Path]
    Files.walkFileTree(
      jarPath,
      new SimpleFileVisitor[Path]() {
        private var currentTarget: Path = _

        override def preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult = {
          currentTarget = targetDir.resolve(jarPath.relativize(dir).toString)
          Files.createDirectories(currentTarget)
          FileVisitResult.CONTINUE
        }

        override def visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult = {
          val target = targetDir.resolve(jarPath.relativize(file).toString)
          targets += target
          Files.copy(
            file,
            target,
            StandardCopyOption.REPLACE_EXISTING,
          )
          FileVisitResult.CONTINUE
        }
      },
    )

    RecursiveCopyOutput(targets.toSeq) // 2.13 compat
  }

  case class ContentIterator(files: Iterable[FileContent])

  def enumerateClasspath(sourcePath: String): ContentIterator = {
    val pathReference = getPath(sourcePath)
    if (pathReference.isEmpty) {
      return ContentIterator(Iterable.empty)
    }

    val jarPath: Path = pathReference.get.path

    val targets = mutable.ArrayBuffer.empty[FileContent]

    Files.walkFileTree(
      jarPath,
      new SimpleFileVisitor[Path]() {
        override def preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult = {
          FileVisitResult.CONTINUE
        }

        override def visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult = {
          val relativePath = jarPath.relativize(file)
          targets += FileContent(relativePath, Files.readAllBytes(file))
          FileVisitResult.CONTINUE
        }
      },
    )

    ContentIterator(targets.toSeq)
  }

  def read(fileName: String): Option[InputStream] = {
    Option(clazz.getClassLoader.getResourceAsStream(fileName))
  }

  def readAsString(fileName: String): Option[String] = {
    read(fileName).map {
      is =>
        val reader = new BufferedReader(new InputStreamReader(is))
        reader.lines.collect(Collectors.joining(System.lineSeparator))
    }
  }

}

object IzResources extends IzResources(IzManifest.getClass) {

  case class FileContent(path: Path, content: Array[Byte])

  class PathReference(val path: Path, val fileSystem: FileSystem) extends AutoCloseable {
    override def close(): Unit = {
      if (this.fileSystem != null) this.fileSystem.close()
    }
  }

  case class RecursiveCopyOutput(files: Seq[Path])

  object RecursiveCopyOutput {
    def empty: RecursiveCopyOutput = RecursiveCopyOutput(Seq.empty)
  }

  implicit def toResources(clazz: Class[_]): IzResources = new IzResources(clazz)

  object ResourceLocation {

    final case class Filesystem(file: File) extends ResourceLocation

    final case class Jar(jarPath: File, jar: JarFile, entry: ZipEntry) extends ResourceLocation

    case object NotFound extends ResourceLocation

  }

}
