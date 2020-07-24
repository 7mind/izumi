package izumi.distage.constructors

import izumi.fundamentals.platform.properties

/**
  * Java properties that control debug output of [[AnyConstructor]] & [[izumi.distage.model.providers.Functoid]] macros
  *
  * @see [[DebugProperties]]
  */
object DebugProperties extends properties.DebugProperties {
  final val `izumi.debug.macro.distage.constructors` = BoolProperty("izumi.debug.macro.distage.constructors")
  final val `izumi.debug.macro.distage.providermagnet` = BoolProperty("izumi.debug.macro.distage.providermagnet")
}
