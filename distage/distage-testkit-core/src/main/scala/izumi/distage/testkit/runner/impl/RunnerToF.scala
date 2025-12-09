package izumi.distage.testkit.runner.impl

import izumi.functional.quasi.{QuasiAsync, QuasiIO, QuasiIORunner}

trait RunnerToF[F[_]] {
  def runToF[G[_], A](runner: QuasiIORunner[G], f: () => G[A]): F[A]
}

object RunnerToF extends RunnerToFPlatformSpecific {

  final class AsyncImpl[F[_]](
    F: QuasiIO[F],
    FA: QuasiAsync[F],
  ) extends RunnerToF[F] {
    override def runToF[G[_], A](runner: QuasiIORunner[G], f: () => G[A]): F[A] = {
      F.suspendF {
        val (future, interrupt) = runner.runFutureInterruptible(f())
        F.guarantee {
          FA.fromFuture(future)
        }(`finally` = FA.fromFuture(interrupt.apply()))
      }
    }
  }

}
