package izumi.fundamentals.platform.logging

/**
  * Marker trait for objects that hold names of Java Properties
  * that control Debug Output for macros, internals of `izumi` or
  * other libraries.
  *
  * Search for inheritors of this trait to discover debugging java properties
  *
  * e.g.
  *
  * To see macro debug output during compilation, set `-Dizumi.debug.macro.rtti=true` java property! e.g.
  * {{{
  *  sbt -Dizumi.debug.macro.rtti=true compile
  * }}}
  */
trait DebugProperties
