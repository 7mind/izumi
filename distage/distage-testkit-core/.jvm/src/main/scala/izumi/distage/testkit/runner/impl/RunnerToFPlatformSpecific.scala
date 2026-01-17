package izumi.distage.testkit.runner.impl

import izumi.functional.quasi.{QuasiAsync, QuasiIORunner}

private[impl] trait RunnerToFPlatformSpecific {
  type PlatformDefaultImpl[F[_]] = BlockingImpl[F]

  final class BlockingImpl[F[_]](
    FA: QuasiAsync[F]
  ) extends RunnerToF[F] {
    override def runToF[G[_], A](runner: QuasiIORunner[G], f: () => G[A]): F[A] = {
      // We must use `maybeSuspendInterruptible` (e.g. `IO.interruptible` for cats-effect) instead of
      // plain `maybeSuspend` (e.g. `IO.delay`) because:
      //
      // 1. cats-effect's `unsafeRunSync` executes the effect asynchronously on the compute pool,
      //    not on the calling thread. The calling thread only waits on a blocking queue for the result.
      //
      // 2. When the outer fiber is canceled (e.g., due to test timeout), `IO.delay` blocks are
      //    not interruptible - cancellation waits for them to complete naturally.
      //
      // 3. `IO.interruptible` makes the block respond to fiber cancellation by interrupting the
      //    thread executing the block, allowing `runner.runBlocking` to receive the interrupt
      //    and properly run its finalizers.
      //
      // While ZIO's `unsafe.run` starts execution synchronously on the calling thread (unlike
      // cats-effect which shifts immediately), ZIO can also jump to another thread pool if any
      // async operation is encountered during effect execution. Therefore, we cannot reliably
      // assume the calling thread is the compute thread for either runtime, and must use the
      // interruptible version for both.
      FA.maybeSuspendInterruptible {
        scala.concurrent.blocking {
          runner.runBlocking(f())
        }
      }
    }
  }
}
