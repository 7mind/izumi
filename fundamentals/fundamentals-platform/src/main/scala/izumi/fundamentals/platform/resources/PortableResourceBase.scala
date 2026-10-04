package izumi.fundamentals.platform.resources

import io.github.classgraph.ClassGraph
import izumi.fundamentals.platform.resources.IzIOStreams.*

import java.io.{File, FileInputStream}
import java.nio.file.{Files, Paths}

trait PortableResourceBase {

  protected def doExtractSources[T](
    maybeRoot: Option[String],
    pathExpr: String,
  ): Seq[(String, String)] = {
    val sources = maybeRoot match {
      case Some(value) =>
        val glob = GlobParser.parseGlobExpr(pathExpr)
        val rootDir = Paths.get(value, glob.basePath)

        if (!rootDir.toFile.exists()) {
          Seq.empty
        } else {
          walkTree(rootDir.toFile)
            .map(p => (p.toPath, GlobParser.normalizeSeparators(rootDir.relativize(p.toPath).toFile.getPath)))
            .filter {
              case (p, r) =>
                Files.isRegularFile(p) && GlobParser.matchesPattern(r, glob)
            }
            .map {
              case (p, r) =>
                r -> new FileInputStream(p.toFile).streamToString()
            }
            .toSeq
        }
      case None =>
        Seq.empty
    }
    sources
  }

  protected def extractResourceContents(
    sourcePath: String
  ): Either[String, Seq[(String, String)]] = {
    extractResourceContents(sourcePath, new ClassGraph())
  }

  protected def extractResourceContents(
    sourcePath: String,
    classGraph: ClassGraph,
  ): Either[String, Seq[(String, String)]] = {
    val scanResult = classGraph
      .acceptPaths(sourcePath)
      .disableModuleScanning()
      .disableNestedJarScanning()
      .scan

    try {
      import scala.jdk.CollectionConverters.*
      val found = scanResult.getAllResources.asScala.toSeq.map {
        resource =>
          val stream = resource.open()
          try {
            (resource.getPath, resource.getClasspathElementURI.toString, stream.streamToString())
          } finally {
            stream.close()
          }
      }
      val conflicts = found.groupBy(_._1).toSeq.sortBy(_._1).collect {
        case (path, entries) if entries.map(_._3).distinct.size > 1 =>
          s"$path differs between ${entries.map(_._2).mkString(", ")}"
      }
      if (conflicts.nonEmpty) {
        Left(conflicts.mkString(s"conflicting resources under $sourcePath on the classpath:\n", "\n", ""))
      } else {
        Right(found.map { case (path, _, content) => path -> content }.distinct)
      }
    } finally {
      scanResult.close()
    }
  }

  /**
    * Split `content` into pieces small enough to be emitted as individual JVM constant-pool string
    * literals. A `CONSTANT_Utf8` entry stores its length in an unsigned 16-bit field, so it cannot
    * exceed 65535 bytes of modified UTF-8. A single `char` encodes to at most 3 modified-UTF-8 bytes
    * (lone surrogates included), so chunking at [[maxChunkChars]] characters keeps every chunk under
    * the limit regardless of content. Splitting mid-surrogate-pair is safe: re-concatenating the
    * chunks at runtime reproduces the original string exactly.
    */
  protected def chunkString(content: String): Seq[String] = {
    if (content.length <= maxChunkChars) {
      Seq(content)
    } else {
      content.grouped(maxChunkChars).toSeq
    }
  }

  /** 65535 / 3 == 21845 is the theoretical max; round down for headroom. */
  private final val maxChunkChars: Int = 20000

  protected def walkTree(file: File): Iterable[File] = {
    val children = if (file.isDirectory) {
      Option(file.listFiles).toSeq.flatMap(_.toSeq)
    } else {
      Iterable.empty
    }
    Seq(file) ++ children.flatMap(walkTree)
  }
}
