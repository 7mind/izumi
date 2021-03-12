package izumi.fundamentals.platform.files

import java.io.File
import java.net.URI
import java.nio.file.{FileSystem, Path}

object IzZip {

  final case class ZE(name: String, file: Path)

  def zip(out: Path, files: Iterable[ZE]): Unit = {
    import java.io.{BufferedInputStream, FileInputStream, FileOutputStream}
    import java.util.zip.{ZipEntry, ZipOutputStream}

    val outFile = out.toFile
    if (outFile.exists()) {
      outFile.delete()
    }

    val zip = new ZipOutputStream(new FileOutputStream(outFile))

    files.foreach {
      name =>
        zip.putNextEntry(new ZipEntry(name.name))
        val in = new BufferedInputStream(new FileInputStream(name.file.toFile))
        var b = in.read()
        while (b > -1) {
          zip.write(b)
          b = in.read()
        }
        in.close()
        zip.closeEntry()
    }
    zip.close()
  }

  // zip filesystem isn't thread safe
  def findInZips(zips: Seq[File], predicate: Path => Boolean): Iterable[(Path, String)] = synchronized {
    zips
      .filter(f => f.exists() && f.isFile && (f.getName.endsWith(".jar") || f.getName.endsWith(".zip")))
      .flatMap {
        f =>
          val uri = f.toURI
          val jarUri = URI.create(s"jar:${uri.toString}")
          val fs = IzFiles.getFs(jarUri).get

          try {
            enumerate(predicate, fs)
              .map(path => path -> IzFiles.readString(path))
          } finally {
            fs.close()
          }
      }
  }

  private def enumerate(predicate: Path => Boolean, fs: FileSystem): Iterable[Path] = {
    import scala.jdk.CollectionConverters._

    fs.getRootDirectories.asScala
      .flatMap {
        root =>
          import java.nio.file.Files
          Files
            .walk(root)
            .iterator()
            .asScala
            .filter(predicate)
      }

  }
}
