package izumi.distage.compat

import zio.{Unsafe, ZIO}

trait ZIOTest {
  protected def unsafeRun[E, A](eff: => ZIO[Any, E, A]): A = Unsafe.unsafe(implicit unsafe => zio.Runtime.default.unsafe.run(eff).getOrThrowFiberFailure())
}
