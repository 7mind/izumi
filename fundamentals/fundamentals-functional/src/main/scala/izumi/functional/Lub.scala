package izumi.functional

abstract class Lub[-A, -B, Out]() {
  def fst(a: A): Out
  def snd(b: B): Out
}

object Lub {
  implicit def lub[T]: Lub[T, T, T] = new Lub[T, T, T] {
    override def fst(a: T): T = a

    override def snd(b: T): T = b
  }
}
