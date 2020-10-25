package izumi.distage.framework

import izumi.fundamentals.platform.properties

object DebugProperties extends properties.DebugProperties {
  /**
    * Check if all `makeConfig[T]("config.path")` bindings from [[izumi.distage.config.ConfigModuleDef]] parse correctly
    * during plan checking. This will ensure that default configs are well-formed.
    *
    * Default: `true`
    */
  final val `izumi.distage.plancheck.check-config` = BoolProperty("izumi.distage.plancheck.check-config")

  /**
    * Prevent compile-time plan checks from failing the build and print warnings instead.
    *
    * Default: `false`
    */
  final val `izumi.distage.plancheck.onlywarn` = BoolProperty("izumi.distage.plancheck.onlywarn")

  /** Print debug messages during plan checking. Default: `false` */
  final val `izumi.debug.macro.distage.plancheck` = BoolProperty("izumi.debug.macro.distage.plancheck")
}
