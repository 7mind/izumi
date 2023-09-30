package izumi.fundamentals.platform.files

import izumi.fundamentals.platform.time.IzTime

import java.io.File
import java.time.LocalDateTime

trait FileAttributes { this: FileSearch =>
  /** Unsafe, will recursively iterate the whole directory
    */
  def getLastModifiedRecursively(directory: File): Option[LocalDateTime] = {
    import IzTime.*

    if (!directory.exists()) {
      return None
    }

    if (directory.isDirectory) {
      val dmt = directory.lastModified().asEpochMillisLocal

      val fmt = walk(directory).map(_.toFile.lastModified().asEpochMillisLocal).toSeq

      Some((dmt +: fmt).max)
    } else {
      Some(directory.lastModified().asEpochMillisLocal)
    }
  }

}
