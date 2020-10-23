package izumi.distage.roles

import izumi.fundamentals.platform.properties

object DebugProperties extends properties.DebugProperties {
  /** Do not print warnings if user passes an unknown activation axis or choice on the command-line. Default: `false` */
  final val `izumi.distage.roles.activation.ignore-unknown` = BoolProperty("izumi.distage.roles.activation.ignore-unknown")

  /**
    * Print warnings when there are activations in the application with no specified choice when the application starts,
    * no choices were provided either on the command-line or in default Activation components (`Activation @Id("default")` & `Activation Id("additional")`
    *
    * Default: `true`
    */
  final val `izumi.distage.roles.activation.warn-unset` = BoolProperty("izumi.distage.roles.activation.warn-unset")
}