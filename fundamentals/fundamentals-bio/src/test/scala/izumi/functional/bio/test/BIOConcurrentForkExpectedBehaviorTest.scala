package izumi.functional.bio.test

import izumi.functional.bio.{Concurrent2, Fork2, Panic2, Primitives2, UnsafeRun2}
import izumi.reflect.TagKK
import org.scalatest.Assertion
import org.scalatest.wordspec.AsyncWordSpec

final class BIOConcurrentForkExpectedBehaviorTestZIO extends BIOConcurrentForkExpectedBehaviorTest[zio.IO](UnsafeRun2.createZIO())

abstract class BIOConcurrentForkExpectedBehaviorTest[F[+_, +_]: TagKK: Concurrent2: Primitives2: Fork2](
  runner: UnsafeRun2[F]
) extends AsyncWordSpec {
  val F: Panic2[F] = Concurrent2[F].InnerF

  s"implementor ${TagKK[F].tag} of {Concurrent2,Primitives2,Fork2}" should {

    "have sandbox not catch external interruption, even when uninterruptible" in {
      val test: F[Nothing, Assertion] = for {
        caughtExtInterrupt <- F.mkRef(false)
        l1 <- F.mkLatch
        l2 <- F.mkLatch
        fib = F.uninterruptibleExcept {
          restore =>
            restore(l1.succeed(()) *> l2.await).sandbox.catchAll(_ => caughtExtInterrupt.set(true))
        }
        _ <- F.fork(fib).flatMap(_.interrupt)
        caught <- caughtExtInterrupt.get
      } yield {
        assert(!caught)
      }
      runner.unsafeRunAsyncAsFuture(test).map(_.toThrowableEither.toTry.get)
    }

  }

}
