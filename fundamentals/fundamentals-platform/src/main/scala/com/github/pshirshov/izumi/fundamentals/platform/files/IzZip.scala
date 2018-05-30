package com.github.pshirshov.izumi.fundamentals.platform.files

import java.nio.file.Path

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
}
