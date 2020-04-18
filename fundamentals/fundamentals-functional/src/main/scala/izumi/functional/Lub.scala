package izumi.functional

final class Lub[-A, -B, Out] private[functional] (private val dummy: Boolean = false) extends AnyVal {
  @inline def fst(a: A): Out = a.asInstanceOf[Out]
  @inline def snd(b: B): Out = b.asInstanceOf[Out]
}

object Lub {
  @inline implicit def lub[T]: Lub[T, T, T] = new Lub[T, T, T]
}
