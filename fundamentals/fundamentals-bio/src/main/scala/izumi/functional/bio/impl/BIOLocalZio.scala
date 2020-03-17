package izumi.functional.bio.impl

import izumi.functional.bio.{=!=, BIOLocal, BIOMonad3}
import zio.{NeedsEnv, ZIO}

object BIOLocalZio extends BIOLocalZio

class BIOLocalZio extends BIOLocal[ZIO] {
  @inline override val InnerF: BIOMonad3[ZIO] = BIOAsyncZio

  @inline override def provide[R, E, A](fr: ZIO[R, E, A])(r: => R)(implicit ev: R =!= Any): ZIO[Any, E, A] = fr.provide(r)(NeedsEnv)
  @inline override def provideSome[R, E, A, R0](fr: ZIO[R, E, A])(f: R0 => R)(implicit ev1: R0 =!= Any, ev2: R =!= Any): ZIO[R0, E, A] = fr.provideSome(f)(NeedsEnv)

  @inline override def ask[R]: ZIO[R, Nothing, R] = access[R, R](identity)

  @inline override def access[R, A](f: R => A): ZIO[R, Nothing, A] = ZIO.access(f)
  @inline override def accessThrowable[R, A](f: R => A): ZIO[R, Throwable, A] = accessM(f.andThen(ZIO.effect(_)))
  @inline override def accessM[R, E, A](f: R => ZIO[R, E, A]): ZIO[R, E, A] = ZIO.accessM(f)
}
