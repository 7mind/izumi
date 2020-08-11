package izumi.functional

final class Value[A] private (private val value: A) extends AnyVal {
  @inline def map[B](f: A => B): Value[B] = {
    new Value(f(this.value))
  }

  @inline def mut[C, B](context: Option[C])(f: (A, C) => A): Value[A] = {
    context match {
      case Some(ctx) =>
        new Value(f(this.value, ctx))
      case None =>
        this
    }
  }

  @inline def mut[B](cond: Boolean)(f: A => A): Value[A] = {
    if (cond) {
      new Value(f(this.value))
    } else {
      this
    }
  }

  @inline def eff(f: A => Unit): Value[A] = {
    f(value)
    this
  }

  @inline def get: A = value
}

object Value {
  def apply[A](value: A): Value[A] = new Value[A](value)
}
