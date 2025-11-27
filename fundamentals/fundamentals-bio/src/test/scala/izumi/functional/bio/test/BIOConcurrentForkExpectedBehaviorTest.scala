package izumi.functional.bio.test

import izumi.functional.bio.{Concurrent2, Fork2, Panic2, Primitives2, UnsafeRun2}
import izumi.reflect.TagKK
import org.scalatest.Assertion
import org.scalatest.wordspec.AsyncWordSpec

import scala.concurrent.ExecutionContext

final class BIOConcurrentForkExpectedBehaviorTestZIO
  extends BIOConcurrentForkExpectedBehaviorTest[zio.IO](ec => UnsafeRun2.createZIO(Some(zio.Executor.fromExecutionContext(ec))))

abstract class BIOConcurrentForkExpectedBehaviorTest[F[+_, +_]: TagKK: Concurrent2: Primitives2: Fork2](
  mkRunner: ExecutionContext => UnsafeRun2[F]
) extends AsyncWordSpec {
  val F: Panic2[F] = Concurrent2[F].InnerF
  val runner: UnsafeRun2[F] = mkRunner(this.executionContext)

  s"implementation of {Concurrent2,Primitives2,Fork2} of ${TagKK[F].tag}" should {

    "have sandbox not catch external interruption, even when uninterruptible" in {
      val test: F[Nothing, Assertion] = for {
        caughtExtInterrupt <- F.mkRef(false)
        l1 <- F.mkLatch
        fib = F.uninterruptibleExcept {
          restore =>
            restore(l1.succeed(()) *> F.never).sandbox.catchAll(_ => caughtExtInterrupt.set(true))
        }
        _ <- F.fork(fib).flatMap(l1.await *> _.interrupt)
        caught <- caughtExtInterrupt.get
      } yield {
        assert(!caught)
      }
      runner.unsafeRunAsyncAsFuture(test).map(_.toThrowableEither.toTry.get)
    }

  }

}
