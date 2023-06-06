package zio._izumicompat_

import zio.{Fiber, Trace, ZIO}

object __ZIOWithFiberRuntime {
  def ZIOWithFiberRuntime[R, E, A](onState: (Fiber.Runtime[E, A], Fiber.Status.Running) => ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, A] = {
    ZIO.withFiberRuntime(onState)
  }
}
