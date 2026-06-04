package izumi.distage.roles

import izumi.fundamentals.platform.properties

object DebugProperties extends properties.DebugProperties {
  /**
    * Do not print warnings if user passes an unknown activation axis or choice on the command-line.
    *
    * Default: `false`
    */
  final val `izumi.distage.roles.activation.ignore-unknown` = BoolProperty("izumi.distage.roles.activation.ignore-unknown")

  /**
    * Print warnings when there are activations in the application with no specified choice when the application starts,
    * no choices were provided either on the command-line or in default Activation components (`Activation @Id("default")` & `Activation Id("additional")`
    *
    * Default: `true`
    */
  final val `izumi.distage.roles.activation.warn-unset` = BoolProperty("izumi.distage.roles.activation.warn-unset")

  /**
    * Do not print warnings when roles with an incompatible effect type are discovered and discarded.
    *
    * Default: `false`
    */
  final val `izumi.distage.roles.ignore-mismatched-effect` = BoolProperty("izumi.distage.roles.ignore-mismatched-effect")

  /**
    * Force JSON logging
    *
    * Can be set / overridden via command-line option `--log-format`/`-lf`
    *
    * Default: `false`
    */
  final val `izumi.distage.roles.logs.json` = BoolProperty("izumi.distage.roles.logs.json")

  /**
    * Register the application's `LogRouter` in the process-global [[izumi.logstage.api.routing.StaticLogRouter]]
    * (required for slf4j support).
    *
    * Default: `true`
    */
  final val `izumi.distage.roles.logs.static-log-router` = BoolProperty("izumi.distage.roles.logs.static-log-router")

  /**
    * Include reference role configs as fallback configs if an explicit role config is passed on the command-line.
    *
    * If `false`, explicit role config fully replaces reference role configs instead of overriding them.
    *
    * Default: `true`
    */
  final val `distage.roles.always-include-reference-role-configs` = BoolProperty("distage.roles.always-include-reference-role-configs")

  /**
    * Include reference common configs as fallback configs if an explicit common config is passed on the command-line.
    *
    * If `false`, explicit common config fully replaces reference common configs instead of overriding them.
    *
    * Default: `true`
    */
  final val `distage.roles.always-include-reference-common-configs` = BoolProperty("distage.roles.always-include-reference-common-configs")

  /**
    * Don't use any reference configs, role or common, only read configs passed on the command-line and system properties.
    *
    * Default: `false`
    */
  final val `distage.roles.ignore-all-reference-configs` = BoolProperty("distage.roles.ignore-all-reference-configs")

  /**
    * Enable environment variables of form `CONFIG_FORCE_a_b_c=value` to override values set in system properties and config.
    *
    * Default: `true`
    *
    * @see [[com.typesafe.config.ConfigFactory.systemEnvironmentOverrides]] - documentation for environment variable override syntax
    */
  final val `distage.roles.enable-config-environment-overrides` = BoolProperty("distage.roles.enable-config-environment-overrides")
}
