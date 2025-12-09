package izumi.distage.testkit.runner.impl

import izumi.functional.quasi.{QuasiIO, QuasiIORunner}

private[impl] trait RunnerToFPlatformSpecific {
  type PlatformDefaultImpl[F[_]] = BlockingImpl[F]

  final class BlockingImpl[F[_]](
    F: QuasiIO[F]
  ) extends RunnerToF[F] {
    override def runToF[G[_], A](runner: QuasiIORunner[G], f: () => G[A]): F[A] = {
      F.maybeSuspend {
        scala.concurrent.blocking {
          runner.runBlocking(f())
        }
      }
    }
  }
}
