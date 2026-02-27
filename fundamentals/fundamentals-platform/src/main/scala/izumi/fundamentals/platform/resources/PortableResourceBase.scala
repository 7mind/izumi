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
    sourcePath: String,
  ): Seq[(String, String)] = {
    val scanResult = new ClassGraph()
      .acceptPaths(sourcePath)
      .disableJarScanning()
      .disableModuleScanning()
      .disableNestedJarScanning()
      .scan

    try {
      import scala.jdk.CollectionConverters.*
      scanResult.getAllResources.asScala.toSeq.map {
        resource =>
          val stream = resource.open()
          try {
            resource.getPath -> stream.streamToString()
          } finally {
            stream.close()
          }
      }
    } finally {
      scanResult.close()
    }
  }

  protected def walkTree(file: File): Iterable[File] = {
    val children = if (file.isDirectory) {
      Option(file.listFiles).toSeq.flatMap(_.toSeq)
    } else {
      Iterable.empty
    }
    Seq(file) ++ children.flatMap(walkTree)
  }
}
