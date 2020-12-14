package izumi.distage.framework

import izumi.fundamentals.platform.properties

object DebugProperties extends properties.DebugProperties {
  /**
    * Check if all `makeConfig[T]("config.path")` bindings from [[izumi.distage.config.ConfigModuleDef]] parse correctly
    * during plan checking. This will ensure that default configs are well-formed.
    *
    * Default: `true`
    *
    * @note To affect compile-time, the system property must be set in sbt, `sbt -Dprop=true`, or by adding the option to `.jvmopts` in project root.
    */
  final val `izumi.distage.plancheck.check-config` = BoolProperty("izumi.distage.plancheck.check-config")

  /**
    * Print all the bindings loaded from plugins when a problem is found during plan checking.
    *
    * Default: `false`, due to noisiness of binding printouts
    *
    * @note To affect compile-time, the system property must be set in sbt, `sbt -Dprop=true`, or by adding the option to `.jvmopts` in project root.
    */
  final val `izumi.distage.plancheck.print-bindings` = BoolProperty("izumi.distage.plancheck.print-bindings")

  /**
    * Prevent compile-time plan checks from failing the build and print warnings instead.
    *
    * Default: `false`
    *
    * @note To affect compile-time, the system property must be set in sbt, `sbt -Dprop=true`, or by adding the option to `.jvmopts` in project root.
    */
  final val `izumi.distage.plancheck.only-warn` = BoolProperty("izumi.distage.plancheck.only-warn")

  /**
    *  Print debug me∑ssages during plan checking.
    *
    *  Default: `false`
    *
    *  @note To affect compile-time, the system property must be set in sbt, `sbt -Dprop=true`, or by adding the option to `.jvmopts` in project root.
    */
  final val `izumi.debug.macro.distage.plancheck` = BoolProperty("izumi.debug.macro.distage.plancheck")
}
