package izumi.distage.testkit.runner.impl

import izumi.functional.quasi.{QuasiIO, QuasiIORunner}

private[impl] trait RunnerToFPlatformSpecific {
  final class Impl[F[_]](
    F: QuasiIO[F]
  ) extends RunnerToF[F] {
    override def runToF[G[_], A](runner: QuasiIORunner[G], f: () => G[A]): F[A] = {
      F.maybeSuspend(runner.run(f()))
    }
  }

  implicit def fromQuasiIO[F[_]](implicit F: QuasiIO[F]): RunnerToF[F] = new Impl(F)
}
