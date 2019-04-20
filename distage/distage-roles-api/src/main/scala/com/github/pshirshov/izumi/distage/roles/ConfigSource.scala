package com.github.pshirshov.izumi.distage.roles

sealed trait ConfigSource

object ConfigSource {

  final case class Resource(name: String) extends ConfigSource {
    override def toString: String = s"resource:$name"
  }

  final case class File(file: java.io.File) extends ConfigSource {
    override def toString: String = s"file:$file"
  }

}
