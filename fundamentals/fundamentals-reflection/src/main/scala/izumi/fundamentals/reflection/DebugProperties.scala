package izumi.fundamentals.reflection

import izumi.fundamentals.platform

/**
  * To see macro debug output during compilation, set `-Dizumi.debug.macro.rtti=true` system property! e.g.
  *
  * {{{
  * sbt -Dizumi.debug.macro.rtti=true compile
  * }}}
  *
  * Add compiler option `-Xmacro-settings:izumi.rtti.cache.compile` to enable compile-time caching of computed
  * LightTypeTags. The option is disabled by default since there appears to be little compile-time performance gain from
  * caching, but results may vary.
  *
  * {{{
  * scalacOptions += "-Xmacro-settings:izumi.rtti.cache.compile"
  * }}}
  *
  * Set system property `-Dizumi.rtti.cache.runtime=false` to disable caching for runtime creation of LightTypeTags.
  * Caching is enabled by default for runtime light type tag creation.
  */
object DebugProperties extends platform.logging.DebugProperties {
  final val `izumi.debug.macro.rtti` = "izumi.debug.macro.rtti"
  final val `izumi.rtti.cache.compile` = "izumi.rtti.cache.compile"
  final val `izumi.rtti.cache.runtime` = "izumi.rtti.cache.runtime"
}
