package com.github.pshirshov.izumi.distage.config

import com.github.pshirshov.izumi.distage.config.model.AppConfig
import com.typesafe.config.{Config, ConfigFactory}

case class ConfigPath(parts: Seq[String]) {
  def toPath: String = parts.mkString(".")

  override def toString: String = s"cfg:$toPath"
}


case class ResolvedConfig(source: AppConfig, requiredPaths: Set[ConfigPath]) {

  final def minimized(): Config = {
    import scala.collection.JavaConverters._
    val paths = requiredPaths.map(_.toPath)
    ConfigFactory.parseMap(source.config.root().unwrapped().asScala.filterKeys(key => paths.exists(_.startsWith(key))).asJava)
  }

}
