package izumi.distage.testkit.runner.di

import izumi.functional.quasi.QuasiIORunner

private[di] trait PreparedRuntimeRunnerPlatform[F[_]] extends QuasiIORunner[F] {
  protected def underlying: QuasiIORunner[F]

  override def runBlocking[A](effect: => F[A]): A = underlying.runBlocking(effect)
}
