package izumi.functional.bio.test

import izumi.functional.bio.{Async2, F}
import izumi.reflect.TagKK
import org.scalatest.Assertion

import java.util.concurrent.CompletableFuture
import scala.concurrent.Promise

trait BIOAsyncExpectedBehaviorTestPlatformSpecific[F[+_, +_]] { this: BIOAsyncExpectedBehaviorTest[F] =>

  s"implementor ${TagKK[F].tag} of {Async2,Primitives2,Fork2} on JVM" should {

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
      runner.unsafeRunAsyncAsFuture(test).map(_.toTry.get)
    }

  }

}
