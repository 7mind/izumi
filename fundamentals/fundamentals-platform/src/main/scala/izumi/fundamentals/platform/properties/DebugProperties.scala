package izumi.fundamentals.platform.properties
import izumi.fundamentals.platform.strings.IzString._

/**
  * Marker trait for objects that hold names of Java Properties
  * that control debug output for macros and internals of `izumi` libraries.
  *
  * Search for inheritors of this trait to discover debugging java properties
  *
  * For example, to see macro debug output during compilation, set `-Dizumi.debug.macro.rtti=true` java property
  *
  * {{{
  *  sbt -Dizumi.debug.macro.rtti=true compile
  * }}}
  */
trait DebugProperties {
  case class Property protected (name: String) {
    def asBoolean(default: Boolean): Boolean = {
      System.getProperty(name).asBoolean().getOrElse(default)
    }
  }
}


