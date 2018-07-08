package com.github.pshirshov.izumi.distage.config

case class ConfigPath(parts: Seq[String]) {
  def toPath: String = parts.mkString(".")

  override def toString: String = s"cfg:$toPath"
}
