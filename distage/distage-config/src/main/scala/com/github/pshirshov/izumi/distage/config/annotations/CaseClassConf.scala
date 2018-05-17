package com.github.pshirshov.izumi.distage.config.annotations

/**
* Read case class from typesafe config. The key is the type of the case class.
*
* You may optionally specify a path to the key.
*
* Example:
*
*   case class ServiceConfig(flag: Boolean, num: Int)
*
*   class ServiceImpl(@CaseClassConf() config: ServiceConfig)
*
* Corresponding HOCON config:
*
*   ServiceConfig {
*     flag: true
*     num: 42
*   }
*
* Or:
*
*   class ServiceImpl(@CaseClassConf("my.config.path") config: ServiceConfig)
*
* Config:
*
*   my.config.path.ServiceConfig {
*     flag: true
*     num: 42
*   }
*
*/
final class CaseClassConf(val path: String = "") extends scala.annotation.StaticAnnotation
