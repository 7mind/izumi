package izumi.functional.bio

import cats.data.Kleisli

trait BIOLocal[FR[-_, +_, +_]] extends BIOAsk[FR] {
  @inline final def scope[R, E, A](fr: FR[R, E, A])(env: => R)(implicit ev: R =!= Any): FR[Any, E, A] = provide(fr)(env)
  @inline final def local[R, E, A](fr: FR[R, E, A])(f: R => R)(implicit ev: R =!= Any): FR[R, E, A] = accessM(v => provide(fr)(f(v)))

  @inline final def toKleisli[R, E, A](fr: FR[R, E, A])(implicit ev: R =!= Any): Kleisli[FR[Any, E, ?], R, A] = Kleisli(provide(fr)(_))

  def provide[R, E, A](fr: FR[R, E, A])(env: => R)(implicit ev: R =!= Any): FR[Any, E, A]
  def provideSome[R, E, A, R0](fr: FR[R, E, A])(f: R0 => R)(implicit ev1: R0 =!= Any, ev2: R =!= Any): FR[R0, E, A]
}
