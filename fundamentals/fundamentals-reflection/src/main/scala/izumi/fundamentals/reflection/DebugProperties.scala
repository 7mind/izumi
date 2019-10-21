package izumi.fundamentals.reflection

import izumi.fundamentals.platform.logging.DebugProperties

/**
  * To see macro debug output during compilation, set `-Dizumi.debug.macro.rtti=true` java property! e.g.
  * {{{
  * sbt -Dizumi.debug.macro.rtti=true compile
  * }}}
  */
object DebugProperties extends DebugProperties {
  final val `izumi.debug.macro.rtti` = "izumi.debug.macro.rtti"
}
