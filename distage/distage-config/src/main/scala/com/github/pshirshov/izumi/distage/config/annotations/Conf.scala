package com.github.pshirshov.izumi.distage.config.annotations

/**
  * This annotation is the same as [[AutoConf]] one but does not use binding names
  */
final class Conf(val name: String) extends scala.annotation.StaticAnnotation
