package com.github.pshirshov.izumi.fundamentals.platform.language

private[language] trait Const[_Unit] {
  protected val unit: _Unit

  @inline def forget(trash: LazyDiscarder[_]*): _Unit = {
    val _ = trash
    unit
  }

  implicit final class LazyDiscarder[T](t: => T) {
    @inline def forget: _Unit = {
      Const.this.forget(t)
      unit
    }
  }

}


object Quirks extends Const[Unit] {
  override protected val unit: Unit = ()

  @inline def discard(trash: Any*): Unit = {
    val _ = trash
  }

  implicit final class Discarder[T](private val t: T) extends AnyVal {
    @inline def discard(): Unit = Quirks.discard(t)
  }

}
