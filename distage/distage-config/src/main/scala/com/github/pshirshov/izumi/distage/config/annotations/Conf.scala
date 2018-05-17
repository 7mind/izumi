package com.github.pshirshov.izumi.distage.config.annotations

/**
  * This annotation is the same as [[AutoConf]] one but does not use binding names
  *
  * Use this one when you need to inject one config section into <i>several</i> components
  */
final class Conf(val name: String) extends scala.annotation.StaticAnnotation


