package izumi.functional.bio.test

import izumi.functional.bio.impl.MiniBIOAsync
import izumi.functional.bio.{Exit, F}
import org.scalatest.wordspec.AsyncWordSpec

import scala.concurrent.duration.*
import scala.concurrent.{Future, Promise}
import scala.util.Success

class MiniBIOAsyncTest extends AsyncWordSpec with MiniBIOAsyncTestPlatformSpecific {

  import MiniBIOAsync.WeakAsyncForMiniBIOAsync

  "MiniBIOAsync" should {

    "support async" in {
      val promise = Promise[Int]()
      val effect = F.async[Throwable, Int] {
        cb =>
          promise.future.onComplete {
            case scala.util.Success(v) => cb(Right(v))
            case scala.util.Failure(e) => cb(Left(e))
          }
      }
      val future = effect.runOnEC(executionContext).map {
        case Exit.Success(value) => assert(value == 777)
        case _ => fail("Expected Success")
      }
      executionContext.execute(() => promise.success(777))
      future
    }

    "support fromFuture" in {
      val future = Future(777)
      F.fromFuture(future).runOnEC(executionContext).map {
        case Exit.Success(value) => assert(value == 777)
        case _ => fail("Expected Success")
      }
    }

    "fromFuture should be interruptible" in {
      val gate = Promise[Unit]()
      val runner = MiniBIOAsync.UnsafeRunMiniBIOAsync(using executionContext)
      val (future, interrupt) = runner.unsafeRunAsyncAsInterruptibleFuture(F.fromFuture(gate.future))
      val result = for {
        _ <- interrupt.interrupt.runOnEC(executionContext)
        exit <- withTimeout(future, 2.seconds)
      } yield {
        exit match {
          case Exit.Termination(t, _, _) => assert(t.isInstanceOf[InterruptedException])
          case other => fail(s"Expected Termination(InterruptedException), got $other")
        }
      }
      result
    }

    "async should be interruptible" in {
      val started = Promise[Unit]()
      val effect = F.async[Throwable, Unit] { _ =>
        started.success(())
      }
      val runner = MiniBIOAsync.UnsafeRunMiniBIOAsync(using executionContext)
      val (future, interrupt) = runner.unsafeRunAsyncAsInterruptibleFuture(effect)
      val result = for {
        _ <- started.future
        _ <- interrupt.interrupt.runOnEC(executionContext)
        exit <- withTimeout(future, 2.seconds)
      } yield {
        exit match {
          case Exit.Termination(t, _, _) => assert(t.isInstanceOf[InterruptedException])
          case other => fail(s"Expected Termination(InterruptedException), got $other")
        }
      }
      result
    }

    "support parTraverse" in {
      val items = List(1, 2, 3)
      val effect = F.parTraverse(items)(x => F.pure(x * 10))
      effect.runOnEC(executionContext).map {
        case Exit.Success(value) => assert(value == List(10, 20, 30))
        case _ => fail("Expected Success")
      }
    }

    "support parTraverse_" in {
      var count = 0
      val items = List(1, 2, 3, 4, 5, 6)
      val effect = F.parTraverse_(items)(_ => F.sync(count += 1))
      effect.runOnEC(executionContext).map {
        case Exit.Success(_) => assert(count == 6)
        case _ => fail("Expected Success")
      }
    }

    "support parTraverseN" in {
      val items = List(1, 2, 3, 4, 5, 6)
      val effect = F.parTraverseN(3)(items)(x => F.sync(x * 10))
      effect.runOnEC(executionContext).map {
        case Exit.Success(value) => assert(value == List(10, 20, 30, 40, 50, 60))
        case _ => fail("Expected Success")
      }
    }

    "support parTraverseN_" in {
      var count = 0
      val items = List(1, 2, 3, 4, 5, 6)
      val effect = F.parTraverseN_(3)(items)(_ => F.sync(count += 1))
      effect.runOnEC(executionContext).map {
        case Exit.Success(_) => assert(count == 6)
        case _ => fail("Expected Success")
      }
    }

    "support parTraverseN with failure" in {
      val items = List(1, 2, 3, 4, 5, 6)
      val effect = F.parTraverseN(3)(items)(x => if (x == 5) F.fail(new RuntimeException("Test error")) else F.unit)
      effect.runOnEC(executionContext).map {
        case Exit.Error(e, _) => assert(e.getMessage == "Test error")
        case _ => fail("Expected Error")
      }
    }

    "support parTraverseN_ with failure" in {
      val items = List(1, 2, 3, 4, 5, 6)
      val effect = F.parTraverseN_(3)(items)(x => if (x == 5) F.fail(new RuntimeException("Test error")) else F.unit)
      effect.runOnEC(executionContext).map {
        case Exit.Error(e, _) => assert(e.getMessage == "Test error")
        case _ => fail("Expected Error")
      }
    }

    "parTraverse executes in parallel, not sequentially" in {
      val promise = Promise[Unit]()

      val result = F.parTraverse(
        List(
          blockingAwait(promise),
          F.sync(promise.complete(Success(()))),
        )
      )(identity)

      result
        .runOnEC(parallelEc).map {
          case Exit.Success(value) => assert(value.size == 2)
          case _ => fail("Expected Success")
        }(using parallelEc)
    }

    "parTraverse_ executes in parallel, not sequentially" in {
      val promise = Promise[Unit]()

      val result = F.parTraverse_(
        List(
          blockingAwait(promise),
          F.sync(promise.complete(Success(()))),
        )
      )(F.map(_)(_ => ()))

      result
        .runOnEC(parallelEc).map {
          case Exit.Success(_) => succeed
          case _ => fail("Expected Success")
        }(using parallelEc)
    }

    "parTraverseN executes in parallel, not sequentially" in {
      val promise = Promise[Unit]()

      val result = F.parTraverseN(2)(
        List(
          blockingAwait(promise),
          F.sync(promise.complete(Success(()))),
        )
      )(identity)

      result
        .runOnEC(parallelEc).map {
          case Exit.Success(value) => assert(value.size == 2)
          case _ => fail("Expected Success")
        }(using parallelEc)
    }

    "parTraverseN_ executes in parallel, not sequentially" in {
      val promise = Promise[Unit]()

      val result = F.parTraverseN_(2)(
        List(
          blockingAwait(promise),
          F.sync(promise.complete(Success(()))),
        )
      )(F.map(_)(_ => ()))

      result
        .runOnEC(parallelEc).map {
          case Exit.Success(_) => succeed
          case _ => fail("Expected Success")
        }(using parallelEc)
    }

    "multiple flatMaps after async should all execute (stack continuation bug regression test)" in {
      val promise = Promise[Int]()

      // Create an async operation followed by multiple flatMaps (left-associated to expose the bug)
      val asyncOp = F.async[Throwable, Int] {
        cb =>
          promise.future.onComplete {
            case scala.util.Success(v) => cb(Right(v))
            case scala.util.Failure(e) => cb(Left(e))
          }
      }
      val effect = F.flatMap(F.flatMap(F.flatMap(asyncOp)(x => F.pure(x + 1)))(y => F.pure(y * 10)))(z => F.pure(z + 5))

      val future = effect.runOnEC(executionContext)
      executionContext.execute(() => promise.success(1))

      future.map {
        case Exit.Success(value) => assert(value == 25, s"Expected 25 but got $value - stack continuation is broken!")
        case e => fail(s"Expected Success but got Error: $e")
      }
    }

  }

}
