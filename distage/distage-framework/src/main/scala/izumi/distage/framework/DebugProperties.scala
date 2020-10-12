package izumi.distage.framework

import izumi.fundamentals.platform.properties.DebugProperties

object DebugProperties extends DebugProperties {
  final val `distage.plancheck.check-config` = BoolProperty("distage.plancheck.check-config")

  /** Print full plan when a problem is found during plan checking. `false` by default, due to noisiness of large plan printouts */
  final val `distage.plancheck.print-plan` = BoolProperty("distage.plancheck.print-plan")

  /** Enable debug prints during plan checking */
  final val `distage.plancheck.debug` = BoolProperty("distage.plancheck.debug")

  final val `distage.plancheck.max-activations` = StrProperty("distage.plancheck.max-activations")
}
