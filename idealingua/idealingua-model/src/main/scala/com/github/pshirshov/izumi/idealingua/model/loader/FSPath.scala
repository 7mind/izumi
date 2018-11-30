package com.github.pshirshov.izumi.idealingua.model.loader

import java.nio.file.Path

sealed trait FSPath {
  def name: String
}

object FSPath {
  final case class Full(location: String, name: String) extends FSPath
  final case class Name(name: String) extends FSPath

  def apply(path: Path): FSPath = {
    val name = path.getFileName.toString

    Option(path.getParent) match {
      case Some(p) =>
        Full(p.toString, name)

      case None =>
        Name(name)
    }
  }

  def apply(pkg: Seq[String]): FSPath = {
    if (pkg.init.nonEmpty) {
      FSPath.Full(pkg.init.mkString("/"), pkg.last)
    } else {
      FSPath.Name(pkg.last)
    }
  }

  def apply(path: String): FSPath = {
    apply(path.split('/'))
  }
}
