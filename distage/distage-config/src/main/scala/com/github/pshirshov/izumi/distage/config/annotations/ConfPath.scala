package com.github.pshirshov.izumi.distage.config.annotations

/**
  * This annotation allows to define config path explicitly, without any magic
  *
  * Use this annotation when you are too lazy to learn how [[AutoConf]] and [[Conf]] work
  */
final class ConfPath(val path: String) extends scala.annotation.StaticAnnotation
