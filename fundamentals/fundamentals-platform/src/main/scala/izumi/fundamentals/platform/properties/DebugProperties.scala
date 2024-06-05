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
  @inline protected final def BoolProperty(name: String): BooleanProperty = new BooleanProperty(name)

  @inline protected final def StrProperty(name: String): StrProperty = new StrProperty(name)
}

final case class BooleanProperty(name: String) extends AnyVal {
  def boolValue(default: Boolean): Boolean = {
    System.getProperty(name).asBoolean().getOrElse(default)
  }
}

final case class StrProperty(name: String) extends AnyVal {
  def strValue(default: String): String = {
    val str = System.getProperty(name)
    if (str ne null) str else default
  }

  def strValue(): Option[String] = {
    Option(System.getProperty(name))
  }
}
