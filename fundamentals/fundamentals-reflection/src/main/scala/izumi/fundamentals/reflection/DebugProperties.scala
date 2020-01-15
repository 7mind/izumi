package izumi.fundamentals.reflection

import izumi.fundamentals.platform.properties.DebugProperties

/**
  * Java properties and macro settings that control behavior and debug output of Lightweight Reflection macros
  *
  * @see [[DebugProperties]]
  */
object DebugProperties extends DebugProperties {
  /**
    * To see macro debug output during compilation, set `-Dizumi.debug.macro.rtti=true` system property!
    *
    * {{{
    *   sbt -Dizumi.debug.macro.rtti=true compile
    * }}}
    */
  final val `izumi.debug.macro.rtti` = "izumi.debug.macro.rtti"

  /**
    * Add compiler option `-Xmacro-settings:izumi.rtti.cache.compile=false` to disable compile-time caching of computed
    * LightTypeTags. Caching is enabled by default for compile-time light type tag creation.
    *
    * {{{
    *   scalacOptions += "-Xmacro-settings:izumi.rtti.cache.compile=false"
    * }}}
    */
  final val `izumi.rtti.cache.compile` = "izumi.rtti.cache.compile"

  /**
    * Set system property `-Dizumi.rtti.cache.runtime=false` to disable caching for runtime creation of LightTypeTags.
    * Caching is enabled by default for runtime light type tag creation.
    *
    * {{{
    *   sbt -Dizumi.rtti.cache.runtime=false
    * }}}
    */
  final val `izumi.rtti.cache.runtime` = "izumi.rtti.cache.runtime"
}
