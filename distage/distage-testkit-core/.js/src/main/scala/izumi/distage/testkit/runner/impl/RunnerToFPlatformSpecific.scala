package izumi.distage.testkit.runner.impl

import izumi.functional.quasi.{QuasiAsync, QuasiIORunner}

private[impl] trait RunnerToFPlatformSpecific {
  final class Impl[F[_]](A: QuasiAsync[F]) extends RunnerToF[F] {
    override def runToF[G[_], A](runner: QuasiIORunner[G], f: () => G[A]): F[A] = {
      A.fromFuture(runner.runFuture(f()))
    }
  }

  implicit def fromQuasiAsync[F[_]](implicit A: QuasiAsync[F]): RunnerToF[F] = new Impl(A)
}
