package izumi.distage.testkit.runner.impl

import izumi.functional.bio.{Async1, IO1, IORunner1}

trait RunnerToF[F[_]] {
  def runToF[G[_], A](runner: IORunner1[G], f: () => G[A]): F[A]
}

object RunnerToF extends RunnerToFPlatformSpecific {

  final class AsyncImpl[F[_]](
    F: IO1[F],
    FA: Async1[F],
  ) extends RunnerToF[F] {
    override def runToF[G[_], A](runner: IORunner1[G], f: () => G[A]): F[A] = {
      F.suspendF {
        val (future, interrupt) = runner.runFutureInterruptible(f())
        F.guarantee {
          FA.fromFuture(future)
        }(`finally` = FA.fromFuture(interrupt.apply()))
      }
    }
  }

}
