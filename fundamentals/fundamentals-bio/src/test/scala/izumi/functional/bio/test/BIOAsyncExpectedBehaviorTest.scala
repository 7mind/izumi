package izumi.functional.bio.test

import izumi.functional.bio.{Async2, F, Fork2, UnsafeRun2}
import izumi.reflect.TagKK
import org.scalatest.Assertion
import org.scalatest.wordspec.AsyncWordSpec

import java.util.concurrent.CompletableFuture
import scala.concurrent.{ExecutionContext, Promise}

final class BIOAsyncExpectedBehaviorTestZIO extends BIOAsyncExpectedBehaviorTest[zio.IO](ec => UnsafeRun2.createZIO(Some(zio.Executor.fromExecutionContext(ec))))

abstract class BIOAsyncExpectedBehaviorTest[F[+_, +_]: TagKK: Async2: Fork2](
  mkRunner: ExecutionContext => UnsafeRun2[F]
) extends AsyncWordSpec {
  val runner: UnsafeRun2[F] = mkRunner(this.executionContext)

  s"implementor ${TagKK[F].tag} of {Async2,Primitives2,Fork2}" should {

    "have fromFutureJava call cancel when interrupted" in {
      val cs = new CompletableFuture[Unit]()
      val test: F[Throwable, Assertion] = for {
        l1 <- F.sync(Promise[Unit]())
        fiber = F.fromFutureJava { l1.success(()); cs }
        _ <- F.fork(fiber).flatMap(F.fromFuture(_ => l1.future) *> _.interrupt)
        canceled <- F.sync(cs.isCancelled)
      } yield {
        assert(canceled)
      }
      runner.unsafeRunAsyncAsFuture(test).map(_.toThrowableEither.toTry.get)
    }

    "have interruptible fromFuture" in {
      val test: F[Throwable, Assertion] = for {
        l1 <- F.sync(Promise[Unit]())
        l2 <- F.sync(Promise[Unit]())
        fiber = F.fromFuture {
          _ =>
            l1.success(())
            l2.future
        }
        _ <- F.fork(fiber).flatMap(F.fromFuture(_ => l1.future) *> _.interrupt)
      } yield {
        succeed
      }
      runner.unsafeRunAsyncAsFuture(test).map(_.toThrowableEither.toTry.get)
    }

  }

}
