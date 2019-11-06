package izumi.fundamentals.reflection

import izumi.fundamentals.platform

/**
  * To see macro debug output during compilation, set `-Dizumi.debug.macro.rtti=true` system property! e.g.
  *
  * {{{
  * sbt -Dizumi.debug.macro.rtti=true compile
  * }}}
  *
  * Add compiler option `-Xmacro-settings:izumi.rtti.cache.compile=false` to disable compile-time caching of computed
  * LightTypeTags. Caching is enabled by default for compile-time light type tag creation.
  *
  * {{{
  * scalacOptions += "-Xmacro-settings:izumi.rtti.cache.compile=false"
  * }}}
  *
  * Set system property `-Dizumi.rtti.cache.runtime=false` to disable caching for runtime creation of LightTypeTags.
  * Caching is enabled by default for runtime light type tag creation.
  *
  * {{{
  * sbt -Dizumi.rtti.cache.runtime=false
  * }}}
  */
object DebugProperties extends platform.logging.DebugProperties {
  final val `izumi.debug.macro.rtti` = "izumi.debug.macro.rtti"
  final val `izumi.rtti.cache.compile` = "izumi.rtti.cache.compile"
  final val `izumi.rtti.cache.runtime` = "izumi.rtti.cache.runtime"
}
