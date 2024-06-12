package izumi.distage.constructors

import izumi.fundamentals.platform.properties

/**
  * Java properties that control debug output of [[izumi.distage.constructors]] macros
  *
  * @see [[DebugProperties]]
  * @see [[izumi.distage.reflection.macros.universe.DebugProperties]] for [[izumi.distage.model.providers.Functoid]] debug output
  */
object DebugProperties extends properties.DebugProperties {
  final val `izumi.debug.macro.distage.constructors` = BoolProperty("izumi.debug.macro.distage.constructors")
  final val `izumi.distage.rendering.colored` = BoolProperty("izumi.distage.rendering.colored")
  final val `izumi.distage.rendering.colored.forced` = BoolProperty("izumi.distage.rendering.colored.forced")
}
