package com.github.pshirshov.izumi.fundamentals.platform.files

import java.io.File
import java.net.URI
import java.nio.file.{FileSystems, Path, Paths}
import java.util.Collections

import scala.util.Try

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

    files.foreach { name =>
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

  def findInZips(incPath: Path, zips: Seq[File]): Option[String] = {
    zips
      .filter(f => f.exists() && f.isFile && (f.getName.endsWith(".jar") || f.getName.endsWith(".zip")))
      .flatMap {
        f =>
          import scala.collection.JavaConverters._
          val uri = f.toURI
          val jarUri = URI.create(s"jar:${uri.toString}")
          val toFind = Paths.get("/").resolve(incPath)

          val maybeFs = Try(FileSystems.getFileSystem(jarUri))
            .recover {
              case _ =>
                FileSystems.newFileSystem(jarUri, Collections.emptyMap[String, Any]())
            }

          maybeFs
            .get
            .getRootDirectories
            .asScala
            .flatMap {
              root =>
                import java.nio.file.Files
                Files.walk(root)
                  .iterator()
                  .asScala
                  .filter {
                    path =>
                      path.toString == toFind.toString
                  }
            }
      }
      .map(path => IzFiles.readString(path))
      .headOption
  }
}
