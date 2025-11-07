package izumi.distage.testkit.runner.impl

import izumi.functional.quasi.QuasiIORunner

trait RunnerToF[F[_]] {
  def runToF[G[_], A](runner: QuasiIORunner[G], f: () => G[A]): F[A]
}

object RunnerToF extends RunnerToFPlatformSpecific
