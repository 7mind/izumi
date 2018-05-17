package com.github.pshirshov.izumi.distage.config.annotations

/**
  * This annotation tells config resolution mechanism to use all the context information
  * to resolve config entry, namely:
  * - Config class name
  * - Binding class name for the binding requiring the config value
  * - Binding name for the binding requiring the config value
  *
  * So, altogether config path structure for autoconf entry would be
  *
  * {binding_type|fq_binding_type}.{config_type|fq_config_type}.{binding_name?|%}
  */
final class AutoConf extends scala.annotation.StaticAnnotation
