package izumi.fundamentals.platform.language

import scala.language.implicitConversions

/**
  * Syntax for explicitly discarding values to satisfy -Ywarn-value-discard,
  * and for clarity of course!
  *
  * @see also [[izumi.fundamentals.platform.language.unused]]
  **/
object Quirks {

  @inline final def discard(@unused trash: Any*): Unit = ()

  @inline final def forget(@unused trash: LazyDiscarder[_]*): Unit = ()

  @inline implicit final class Discarder[T](private val t: T) extends AnyVal {
    @inline def discard(): Unit = ()
  }

  @inline implicit final def LazyDiscarder[T](@unused t: => T): LazyDiscarder[Unit] = new LazyDiscarder[Unit]()

  @inline final class LazyDiscarder[U >: Unit](private val dummy: Boolean = false) extends AnyVal {
    @inline def forget: U = ()
  }

}
