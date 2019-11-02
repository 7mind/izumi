package izumi.idealingua.il.loader

import izumi.idealingua.model.loader.FSPath

trait FilesystemEnumerator {
  def enumerate(): Map[FSPath, String]
}

object FilesystemEnumerator {

  class Pseudo(files: Map[String, String]) extends FilesystemEnumerator {
    override def enumerate(): Map[FSPath, String] = {
      files.map {
        case (path, content) =>
          FSPath.parse(path) -> content
      }
    }
  }

}
