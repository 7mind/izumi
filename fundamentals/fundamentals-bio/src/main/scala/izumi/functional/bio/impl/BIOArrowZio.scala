package izumi.functional.bio.impl

import izumi.functional.bio.{BIOArrow, BIOMonad3}
import zio.{NeedsEnv, ZIO}

object BIOArrowZio extends BIOArrowZio

class BIOArrowZio extends BIOArrow[ZIO] {
  @inline override final val InnerF: BIOMonad3[ZIO] = BIOAsyncZio

  @inline override final def ask[R]: ZIO[R, Nothing, R] = ZIO.environment
  @inline override final def askWith[R, A](f: R => A): ZIO[R, Nothing, A] = ZIO.access(f)

  @inline override final def access[R, E, A](f: R => ZIO[R, E, A]): ZIO[R, E, A] = ZIO.accessM(f)

  @inline override final def provide[R, E, A](fr: ZIO[R, E, A])(r: => R): ZIO[Any, E, A] = fr.provide(r)(NeedsEnv)
  @inline override final def contramap[R, E, A, R0](fr: ZIO[R, E, A])(f: R0 => R): ZIO[R0, E, A] = fr.provideSome(f)(NeedsEnv)
}
