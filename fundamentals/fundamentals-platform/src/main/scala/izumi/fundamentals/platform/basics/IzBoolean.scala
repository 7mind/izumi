package izumi.fundamentals.platform.basics

import izumi.fundamentals.platform.IzPlatformPureUtil

import scala.language.implicitConversions

trait IzBoolean extends IzPlatformPureUtil {
  import IzBoolean.LazyBool

  @inline implicit final def toLazyBool(b: => Boolean): LazyBool = new LazyBool(() => b)

  @inline final def all(b1: Boolean, b: LazyBool*): Boolean = {
    b1 && b.forall(_.value)
  }

  @inline final def any(b1: Boolean, b: LazyBool*): Boolean = {
    b1 || b.exists(_.value)
  }
}

object IzBoolean extends IzBoolean {
  @inline final implicit class LazyBool(private val b: () => Boolean) extends AnyVal {
    @inline def value: Boolean = b()
    @inline def toInt: Int = if (b()) 1 else 0
  }
}
