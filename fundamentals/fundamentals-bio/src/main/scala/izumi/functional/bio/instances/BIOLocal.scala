package izumi.functional.bio.instances

import zio.NeedsEnv

trait BIOLocal[FR[-_, +_, +_]] extends BIOAsk[FR] {
  final def scope[R, E, A](fr: FR[R, E, A])(env: => R)(implicit ev: NeedsEnv[R]): FR[Any, E, A] = provide(fr)(env)
  final def local[R, E, A](fr: FR[R, E, A])(f: R => R)(implicit ev: NeedsEnv[R]): FR[R, E, A] = accessM(v => provide(fr)(f(v)))

  def provide[R, E, A](fr: FR[R, E, A])(env: => R)(implicit ev: NeedsEnv[R]): FR[Any, E, A]
  def provideSome[R, E, A, R0](fr: FR[R, E, A])(f: R0 => R)(implicit ev: NeedsEnv[R]): FR[R0, E, A]
}

