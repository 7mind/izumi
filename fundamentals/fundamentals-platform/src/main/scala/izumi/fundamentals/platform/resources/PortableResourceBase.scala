package izumi.fundamentals.platform.resources

import io.github.classgraph.ClassGraph
import izumi.fundamentals.platform.resources.IzIOStreams.*
import izumi.fundamentals.platform.resources.IzResources.toResources

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
            .map(p => (p.toPath, rootDir.relativize(p.toPath).toFile.getPath))
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

  protected def doExtractResources(sourcePath: String): List[String] = {
    val scanResult = new ClassGraph()
      .acceptPaths(sourcePath)
      .disableJarScanning()
      .disableModuleScanning()
      .disableNestedJarScanning()
      //      .verbose()
      .scan
    try {
      import scala.jdk.CollectionConverters.*
      val resourceNames = scanResult.getAllResources.getPaths.asScala.toList
      resourceNames
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

  protected def convertResources(r: List[String], classLoader: ClassLoader): Map[String, String] = {
    r
      .map {
        r =>
          val data = classLoader.readAsString(r)
          (r, data)
      }
      .collect {
        case (r, Some(c)) =>
          (r, c)
      }
      .toMap
  }
}
