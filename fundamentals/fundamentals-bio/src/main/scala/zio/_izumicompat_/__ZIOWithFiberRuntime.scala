package zio._izumicompat_

import izumi.fundamentals.platform.language.Quirks.Discarder
import zio.{Fiber, Trace, ZIO}
import zio.stacktracer.TracingImplicits.disableAutoTrace

object __ZIOWithFiberRuntime {
  def ZIOWithFiberRuntime[R, E, A](onState: (Fiber.Runtime[E, A], Fiber.Status.Running) => ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, A] = {
    ZIO.withFiberRuntime(onState)
  }

  disableAutoTrace.discard()
}
