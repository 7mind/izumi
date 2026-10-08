package izumi.functional.bio.test

import izumi.functional.bio.impl.MiniBIOAsync
import izumi.functional.bio.{Exit, F}
import izumi.fundamentals.testkit.AsyncWordSpec
import izumi.distage.testkit.runner.spec.Assertion

import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.duration.*
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.Success

class MiniBIOAsyncTest extends AsyncWordSpec with MiniBIOAsyncTestPlatformSpecific {

  import MiniBIOAsync.WeakAsyncForMiniBIOAsync

  private final val InterruptionTimeout = 2.seconds
  private final val CompletionObservation = 250.millis

  "MiniBIOAsync" should {

    "support async" in {
      val promise = Promise[Int]()
      val future = checkSuccess(asyncPromise(promise), executionContext)(value => assert(value == 777))
      executionContext.execute(() => promise.success(777))
      future
    }

    "support fromFuture" in {
      val future = Future(777)
      checkSuccess(F.fromFuture(future), executionContext)(value => assert(value == 777))
    }

    "fromFuture should be interruptible" in {
      val gate = Promise[Unit]()
      checkInterruption(F.fromFuture(gate.future), Future.successful(()))
    }

    "async should be interruptible" in {
      val started = Promise[Unit]()
      val effect = F.async[Throwable, Unit] { _ =>
        started.success(())
      }
      checkInterruption(effect, started.future)
    }

    "defer interruption until an uninterruptible operation completes" in {
      checkMask(F.uninterruptible(_), expectedContinuations = 1)
    }

    "restore the enclosing mask inside a nested uninterruptible operation" in {
      checkMask(body => F.uninterruptible(F.uninterruptibleExcept(restore => restore(body))), expectedContinuations = 1)
    }

    "restore interruption inside an uninterruptible operation" in {
      checkMask(body => F.uninterruptibleExcept(restore => restore(body)), expectedContinuations = 0)
    }

    "complete an asynchronous finalizer before interrupted execution settles" in {
      val bodyEntered = Promise[Unit]()
      val releaseEntered = Promise[Unit]()
      val releaseGate = Promise[Unit]()
      val acquired = new AtomicInteger(0)
      val released = new AtomicInteger(0)
      val effect = F.bracketCase[Throwable, Int, Unit](F.sync(acquired.incrementAndGet())) {
        (_, _) =>
          F.flatMap(F.async[Nothing, Unit] { callback =>
            releaseEntered.success(())
            releaseGate.future.foreach(_ => callback(Right(())))
          })(_ => F.sync { released.incrementAndGet(); () })
      } { _ =>
        F.async[Throwable, Unit](_ => { bodyEntered.success(()); () })
      }
      val runner = MiniBIOAsync.UnsafeRunMiniBIOAsync(using executionContext)
      val (future, interrupt) = runner.unsafeRunAsyncAsInterruptibleFuture(effect)
      for {
        _ <- withTimeout(bodyEntered.future, InterruptionTimeout)
        _ <- interrupt.interrupt.runOnEC(executionContext)
        _ <- withTimeout(releaseEntered.future, InterruptionTimeout)
        _ <- interrupt.interrupt.runOnEC(executionContext)
        _ = assert(!future.isCompleted)
        _ = releaseGate.success(())
        exit <- withTimeout(future, InterruptionTimeout)
      } yield {
        assertInterrupted(exit)
        assert(acquired.get() == 1)
        assert(released.get() == 1)
      }
    }

    "preserve an independent body failure through interruption during release" in {
      checkReleaseFailure(bodyFails = true)
    }

    "join both zip children when interrupted during held finalization" in {
      checkParallelFinalizers((left, right) => F.zipWithPar(left, right)((_, _) => ()))
    }

    "join all traversal workers when interrupted during held finalization" in {
      checkParallelFinalizers((left, right) => F.parTraverseN_(2)(List(left, right))(identity))
    }

    "preserve an independent finalizer failure through a pending interruption" in {
      checkReleaseFailure(bodyFails = false)
    }

    "release an acquired value when interruption was requested during acquisition" in {
      val acquireEntered = Promise[Unit]()
      val acquireGate = Promise[Unit]()
      val acquired = new AtomicInteger(0)
      val bodies = new AtomicInteger(0)
      val released = new AtomicInteger(0)
      val acquire = F.flatMap(F.async[Throwable, Unit] { callback =>
        acquireEntered.success(())
        acquireGate.future.foreach(_ => callback(Right(())))
      })(_ => F.sync(acquired.incrementAndGet()))
      val effect = F.bracketCase[Throwable, Int, Unit](acquire) { (value, _) =>
        F.sync { assert(value == 1); released.incrementAndGet(); () }
      } { _ =>
        F.sync { bodies.incrementAndGet(); () }
      }
      val runner = MiniBIOAsync.UnsafeRunMiniBIOAsync(using executionContext)
      val (future, interrupt) = runner.unsafeRunAsyncAsInterruptibleFuture(effect)
      for {
        _ <- withTimeout(acquireEntered.future, InterruptionTimeout)
        _ <- interrupt.interrupt.runOnEC(executionContext)
        _ = acquireGate.success(())
        exit <- withTimeout(future, InterruptionTimeout)
      } yield {
        assertInterrupted(exit)
        assert(acquired.get() == 1)
        assert(bodies.get() == 0)
        assert(released.get() == 1)
      }
    }

    "allow interruption in explicitly restored bracket acquisition" in {
      val acquireEntered = Promise[Unit]()
      val released = new AtomicInteger(0)
      val effect = F.bracketExcept[Throwable, Unit, Unit] { restore =>
        restore(F.async[Throwable, Unit](_ => { acquireEntered.success(()); () }))
      } { (_, _) =>
        F.sync { released.incrementAndGet(); () }
      }(_ => F.unit)
      val runner = MiniBIOAsync.UnsafeRunMiniBIOAsync(using executionContext)
      val (future, interrupt) = runner.unsafeRunAsyncAsInterruptibleFuture(effect)
      for {
        _ <- withTimeout(acquireEntered.future, InterruptionTimeout)
        _ <- interrupt.interrupt.runOnEC(executionContext)
        exit <- withTimeout(future, InterruptionTimeout)
      } yield {
        assertInterrupted(exit)
        assert(released.get() == 0)
      }
    }

    "restore interruption after unwinding a failed masked region" in {
      val entered = Promise[Unit]()
      val original = new IllegalStateException("masked failure")
      val effect = F.catchAll[IllegalStateException, Unit, Throwable](F.uninterruptible(F.fail(original))) { error =>
        F.async[Throwable, Unit] { _ =>
          assert(error eq original)
          entered.success(())
          ()
        }
      }
      val runner = MiniBIOAsync.UnsafeRunMiniBIOAsync(using executionContext)
      val (future, interrupt) = runner.unsafeRunAsyncAsInterruptibleFuture(effect)
      for {
        _ <- withTimeout(entered.future, InterruptionTimeout)
        _ <- interrupt.interrupt.runOnEC(executionContext)
        exit <- withTimeout(future, InterruptionTimeout)
      } yield assertInterrupted(exit)
    }

    "stop subsequent effects after self-interruption" in {
      val continuations = new AtomicInteger(0)
      val effect = F.flatMap(F.sendInterruptToSelf)(_ => F.sync { continuations.incrementAndGet(); () })
      runWithExit(effect).map { exit =>
        assertInterrupted(exit)
        assert(continuations.get() == 0)
      }
    }

    "defer self-interruption until the enclosing mask ends" in {
      val maskedContinuations = new AtomicInteger(0)
      val outerContinuations = new AtomicInteger(0)
      val masked = F.uninterruptible(F.flatMap(F.sendInterruptToSelf)(_ => F.sync { maskedContinuations.incrementAndGet(); () }))
      val effect = F.flatMap(masked)(_ => F.sync { outerContinuations.incrementAndGet(); () })
      runWithExit(effect).map { exit =>
        assertInterrupted(exit)
        assert(maskedContinuations.get() == 1)
        assert(outerContinuations.get() == 0)
      }
    }

    "run interruption cleanup once after self-interruption" in {
      val cleaned = new AtomicInteger(0)
      val effect = F.guaranteeOnInterrupt(F.sendInterruptToSelf, _ => F.sync { cleaned.incrementAndGet(); () })
      runWithExit(effect).map { exit =>
        assert(cleaned.get() == 1)
        assert(exit.isInterrupted)
      }
    }

    "bypass sandbox recovery after self-interruption" in {
      val recovered = new AtomicInteger(0)
      val effect = F.flatMap(F.sandboxExit(F.sendInterruptToSelf))(_ => F.sync { recovered.incrementAndGet(); () })
      runWithExit(effect).map { exit =>
        assert(recovered.get() == 0)
        assert(exit.isInterrupted)
      }
    }

    "preserve an independently thrown InterruptedException as a termination" in {
      val original = new InterruptedException("independent termination")
      F.sandboxExit(F.sync(throw original)).runOnEC(executionContext).map {
        case Exit.Success(Exit.Termination(error, _, _)) => assert(error eq original)
        case other => fail(s"Expected sandboxed independent termination, got $other")
      }
    }

    "settle despite a throwing release constructor after body failure" in {
      val original = new IllegalStateException("body failure")
      val releaseError = new IllegalArgumentException("release constructor failure")
      val releases = new AtomicInteger(0)
      val effect = F.bracketCase[Throwable, Unit, Unit](F.unit) { (_, _) =>
        releases.incrementAndGet()
        throw releaseError
      }(_ => F.fail(original))
      withTimeout(runWithExit(effect), InterruptionTimeout).map {
        case Exit.Error(error, _) =>
          assert(error eq original)
          assert(releases.get() == 1)
        case other => fail(s"Expected original body failure, got $other")
      }
    }

    "settle despite a throwing release constructor after interruption" in {
      val releaseError = new IllegalArgumentException("release constructor failure")
      val releases = new AtomicInteger(0)
      val effect = F.bracketCase[Nothing, Unit, Unit](F.unit) { (_, _) =>
        releases.incrementAndGet()
        throw releaseError
      }(_ => F.sendInterruptToSelf)
      withTimeout(runWithExit(effect), InterruptionTimeout).map { exit =>
        assert(exit.isInterrupted)
        assert(releases.get() == 1)
      }
    }

    "support parTraverse" in {
      checkSuccess(F.parTraverse(List(1, 2, 3))(x => F.pure(x * 10)), executionContext)(value => assert(value == List(10, 20, 30)))
    }

    "support parTraverse_" in {
      var count = 0
      val items = List(1, 2, 3, 4, 5, 6)
      val effect = F.parTraverse_(items)(_ => F.sync(count += 1))
      checkSuccess(effect, executionContext)(_ => assert(count == 6))
    }

    "support parTraverseN" in {
      checkSuccess(F.parTraverseN(3)(List(1, 2, 3, 4, 5, 6))(x => F.sync(x * 10)), executionContext)(value => assert(value == List(10, 20, 30, 40, 50, 60)))
    }

    "support parTraverseN_" in {
      var count = 0
      val items = List(1, 2, 3, 4, 5, 6)
      val effect = F.parTraverseN_(3)(items)(_ => F.sync(count += 1))
      checkSuccess(effect, executionContext)(_ => assert(count == 6))
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
      checkParallel(effects => F.parTraverse(effects)(identity))(value => assert(value.size == 2))
    }

    "parTraverse_ executes in parallel, not sequentially" in {
      checkParallel(effects => F.parTraverse_(effects)(F.map(_)(_ => ())))(_ => succeed)
    }

    "parTraverseN executes in parallel, not sequentially" in {
      checkParallel(effects => F.parTraverseN(2)(effects)(identity))(value => assert(value.size == 2))
    }

    "parTraverseN_ executes in parallel, not sequentially" in {
      checkParallel(effects => F.parTraverseN_(2)(effects)(F.map(_)(_ => ())))(_ => succeed)
    }

    "multiple flatMaps after async should all execute (stack continuation bug regression test)" in {
      val promise = Promise[Int]()

      // Create an async operation followed by multiple flatMaps (left-associated to expose the bug)
      val asyncOp = asyncPromise(promise)
      val effect = F.flatMap(F.flatMap(F.flatMap(asyncOp)(x => F.pure(x + 1)))(y => F.pure(y * 10)))(z => F.pure(z + 5))

      val future = effect.runOnEC(executionContext)
      executionContext.execute(() => promise.success(1))

      future.map {
        case Exit.Success(value) => assert(value == 25, s"Expected 25 but got $value - stack continuation is broken!")
        case e => fail(s"Expected Success but got Error: $e")
      }
    }

  }

  private def asyncPromise[A](promise: Promise[A]): MiniBIOAsync[Throwable, A] = F.async { callback =>
    promise.future.onComplete {
      case scala.util.Success(value) => callback(Right(value))
      case scala.util.Failure(error) => callback(Left(error))
    }
  }

  private def checkSuccess[E, A](effect: MiniBIOAsync[E, A], ec: ExecutionContext)(check: A => Assertion): Future[Assertion] = {
    effect.runOnEC(ec).map {
      case Exit.Success(value) => check(value)
      case _ => fail("Expected Success")
    }(using ec)
  }

  private def checkParallel[A](traverse: List[MiniBIOAsync[Throwable, Any]] => MiniBIOAsync[Throwable, A])(check: A => Assertion): Future[Assertion] = {
    val promise = Promise[Unit]()
    val effects = List(blockingAwait(promise), F.sync(promise.complete(Success(()))))
    checkSuccess(traverse(effects), parallelEc)(check)
  }

  private def checkInterruption(effect: MiniBIOAsync[Throwable, Unit], started: Future[Unit]): Future[Assertion] = {
    val (future, interrupt) = MiniBIOAsync.UnsafeRunMiniBIOAsync(using executionContext).unsafeRunAsyncAsInterruptibleFuture(effect)
    for {
      _ <- started
      _ <- interrupt.interrupt.runOnEC(executionContext)
      exit <- withTimeout(future, InterruptionTimeout)
    } yield assertInterrupted(exit)
  }

  private def runWithExit[E, A](effect: MiniBIOAsync[E, A]): Future[Exit[E, A]] = {
    MiniBIOAsync.UnsafeRunMiniBIOAsync(using executionContext).unsafeRunAsyncAsFuture(effect)
  }

  private def checkMask(
    transform: MiniBIOAsync[Throwable, Unit] => MiniBIOAsync[Throwable, Unit],
    expectedContinuations: Int,
  ) = {
    val entered = Promise[Unit]()
    val gate = Promise[Unit]()
    val delivered = Promise[Unit]()
    val continuations = new AtomicInteger(0)
    val body = F.flatMap(F.async[Throwable, Unit] { callback =>
      entered.success(())
      gate.future.foreach { _ =>
        callback(Right(()))
        delivered.success(())
      }
    })(_ => F.sync { continuations.incrementAndGet(); () })
    val runner = MiniBIOAsync.UnsafeRunMiniBIOAsync(using executionContext)
    val (future, interrupt) = runner.unsafeRunAsyncAsInterruptibleFuture(transform(body))
    for {
      _ <- withTimeout(entered.future, InterruptionTimeout)
      _ <- interrupt.interrupt.runOnEC(executionContext)
      _ = gate.success(())
      _ <- withTimeout(delivered.future, InterruptionTimeout)
      exit <- withTimeout(future, InterruptionTimeout)
    } yield {
      assertInterrupted(exit)
      assert(continuations.get() == expectedContinuations)
    }
  }

  private def checkReleaseFailure(bodyFails: Boolean) = {
    val releaseEntered = Promise[Unit]()
    val releaseGate = Promise[Unit]()
    val released = new AtomicInteger(0)
    val original = new IllegalStateException(if (bodyFails) "body failure" else "release failure")
    val body: MiniBIOAsync[Throwable, Unit] = if (bodyFails) F.fail(original) else F.unit
    val effect = F.bracketCase[Throwable, Unit, Unit](F.unit) { (_, _) =>
      F.flatMap(F.async[Nothing, Unit] { callback =>
        releaseEntered.success(())
        releaseGate.future.foreach(_ => callback(Right(())))
      }) { _ =>
        F.sync {
          released.incrementAndGet()
          if (!bodyFails) throw original
          ()
        }
      }
    }(_ => body)
    val runner = MiniBIOAsync.UnsafeRunMiniBIOAsync(using executionContext)
    val (future, interrupt) = runner.unsafeRunAsyncAsInterruptibleFuture(effect)
    for {
      _ <- withTimeout(releaseEntered.future, InterruptionTimeout)
      _ <- interrupt.interrupt.runOnEC(executionContext)
      _ = releaseGate.success(())
      exit <- withTimeout(future, InterruptionTimeout)
    } yield {
      if (bodyFails) {
        exit match {
          case Exit.Error(error, _) => assert(error eq original)
          case other => fail(s"Expected original body failure, got $other")
        }
      } else {
        exit match {
          case Exit.Termination(error, _, _) => assert(error eq original)
          case other => fail(s"Expected original finalizer failure, got $other")
        }
      }
      assert(released.get() == 1)
    }
  }

  private def checkParallelFinalizers(
    combine: (MiniBIOAsync[Throwable, Unit], MiniBIOAsync[Throwable, Unit]) => MiniBIOAsync[Throwable, Unit]
  ) = {
    final class Child {
      val entered = Promise[Unit]()
      val bodyGate = Promise[Unit]()
      val releaseEntered = Promise[Unit]()
      val releaseGate = Promise[Unit]()
      val ended = Promise[Unit]()
      val released = new AtomicInteger(0)
      val effect = F.guarantee(
        F.bracketCase[Throwable, Unit, Unit](F.unit) { (_, _) =>
          F.orTerminate(F.flatMap(F.sync { releaseEntered.success(()); () }) { _ =>
            F.flatMap(F.fromFuture(_ => releaseGate.future))(_ => F.sync { released.incrementAndGet(); () })
          })
        } { _ =>
          F.flatMap(F.sync { entered.success(()); () })(_ => F.fromFuture(_ => bodyGate.future))
        },
        F.sync { ended.success(()); () },
      )
    }
    val left = new Child
    val right = new Child
    val children = Vector(left, right)
    val runner = MiniBIOAsync.UnsafeRunMiniBIOAsync(using executionContext)
    val (future, interrupt) = runner.unsafeRunAsyncAsInterruptibleFuture(combine(left.effect, right.effect))
    val result = for {
      _ <- withTimeout(Future.sequence(children.map(_.entered.future)), InterruptionTimeout)
      _ = left.bodyGate.success(())
      _ <- withTimeout(left.releaseEntered.future, InterruptionTimeout)
      _ <- interrupt.interrupt.runOnEC(executionContext)
      _ <- withTimeout(right.releaseEntered.future, InterruptionTimeout)
      _ <- runWithExit(F.sleep(CompletionObservation))
      _ = assert(!future.isCompleted, "Parallel cancellation must await held child finalizers")
      _ = assert(children.forall(_.released.get() == 0))
      _ <- interrupt.interrupt.runOnEC(executionContext)
      _ = left.releaseGate.success(())
      _ <- withTimeout(left.ended.future, InterruptionTimeout)
      _ = assert(!future.isCompleted, "Parallel cancellation must await the remaining child")
      _ = right.releaseGate.success(())
      exit <- withTimeout(future, InterruptionTimeout)
    } yield {
      assertInterrupted(exit)
      assert(children.forall(_.released.get() == 1))
    }
    result.transformWith { observed =>
      children.foreach { child => val _ = (child.bodyGate.trySuccess(()), child.releaseGate.trySuccess(())) }
      withTimeout(Future.sequence(children.map(_.ended.future)).flatMap(_ => future), InterruptionTimeout)
        .transformWith(_ => Future.fromTry(observed))
    }
  }

  private def assertInterrupted(exit: Exit[Throwable, Unit]) = exit match {
    case Exit.Interruption(error, _, _) => assert(error.isInstanceOf[InterruptedException])
    case other => fail(s"Expected Interruption(InterruptedException), got $other")
  }

}
