package izumi.distage.reflection.macros.universe

/**
  * Java properties that control debug output of [[izumi.distage.model.providers.Functoid]] macros
  *
  * @see [[DebugProperties]]
  */
object DebugProperties extends izumi.fundamentals.platform.properties.DebugProperties {
  final val `izumi.debug.macro.distage.functoid` = BoolProperty("izumi.debug.macro.distage.functoid")
}
