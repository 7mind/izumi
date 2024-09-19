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
    * Discover any component that inherits from [[izumi.distage.roles.model.AbstractRole]] and has a companion object that inherits [[izumi.distage.roles.model.RoleDescriptor]]
    * as a role, not only those additionally added using [[izumi.distage.roles.model.definition.RoleModuleDef#makeRole]],
    * companions of such components will be instantiated reflectively, unlike ones from `RoleModuleDef`.
    *
    * Default: `true`, for the sake of keeping compatibility with code written before [[izumi.distage.roles.model.definition.RoleModuleDef]],
    *          however, reflective instantiation of role companions is deprecated and will be removed in the future,
    *          you are advised to use `RoleModuleDef` instead.
    *
    * @note Flipping this or the corresponding `Boolean @Id("distage.roles.reflection")` component to `false` will speed up launch times
    *
    * @deprecated since 1.0.1
    */
  final val `izumi.distage.roles.reflection` = BoolProperty("izumi.distage.roles.reflection")

  /**
    * Force JSON logging
    *
    * Can be set / overridden via command-line option `--log-format`/`-lf`
    *
    * Default: `false`
    */
  final val `izumi.distage.roles.logs.json` = BoolProperty("izumi.distage.roles.logs.json")

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
}
