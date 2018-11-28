package com.github.pshirshov.izumi.idealingua.il.loader

import com.github.pshirshov.izumi.idealingua.il.loader.model.FSPath

trait FilesystemEnumerator {
  def enumerate(): Map[FSPath, String]
}

object FilesystemEnumerator {
  class Pseudo(files: Map[String, String]) extends FilesystemEnumerator {
    override def enumerate(): Map[FSPath, String] = {
      files.map {
        case (path, content) =>
          val parts = path.split('/')
          val (loc, name) = (parts.init, parts.last)

          val fspath = if (loc.isEmpty) {
            FSPath.Name(name)
          } else {
            FSPath.Full(loc.mkString("/"), name)
          }

          fspath -> content
      }
    }
  }
}
