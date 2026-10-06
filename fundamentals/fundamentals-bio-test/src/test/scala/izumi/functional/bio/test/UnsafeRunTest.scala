package izumi.functional.bio.test

import izumi.functional.bio.{Exit, UnsafeRun2}
import izumi.functional.bio.data.InterruptAction
import izumi.fundamentals.testkit.AsyncWordSpec
import zio.{Executor, ZEnvironment, ZIO}

import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.{Future, Promise}

class UnsafeRunTest extends AsyncWordSpec {
  private final class Started(val completion: Future[Exit[Nothing, Unit]], val interruption: InterruptAction[zio.IO])

  "BIO" should {
    "be able to run on ZIO" in {
      val r = UnsafeRun2.createZIO(customCpuPool = Some(Executor.fromExecutionContext(this.executionContext)))

      r.unsafeRunAsyncAsFuture(zio.ZIO.foreachPar(List(1, 2, 3))(a => zio.ZIO.attempt(a * 2))).map {
        case Exit.Success(value) =>
          assert(value == List(2, 4, 6))
        case f: Exit.Failure[Throwable @unchecked] =>
          fail(f.toThrowable)
      }
    }

    "complete an interrupted ZIO execution Future after finalization" in {
      checkInterruptedCompletion { (runner, effect) =>
        val (completion, interruption) = runner.unsafeRunAsyncAsInterruptibleFuture[Nothing, Unit](effect)
        new Started(completion, interruption)
      }
    }

    "deliver an interrupted ZIO execution callback after finalization" in {
      checkInterruptedCompletion { (runner, effect) =>
        val completion = Promise[Exit[Nothing, Unit]]()
        val interruption = runner.unsafeRunAsyncInterruptible[Nothing, Unit](effect) { exit => val _ = completion.success(exit) }
        new Started(completion.future, interruption)
      }
    }
  }

  private def checkInterruptedCompletion(start: (UnsafeRun2.ZIORunner[Any], zio.UIO[Unit]) => Started) = {
    val executor = Executor.fromExecutionContext(executionContext)
    val runner = UnsafeRun2.createZIO[Any](Some(executor), Some(executor), UnsafeRun2.FailureHandler.Default, Nil, ZEnvironment.empty)
    val entered = Promise[Unit]()
    val released = new AtomicInteger(0)
    val effect = ZIO.acquireReleaseWith(ZIO.unit)(_ => ZIO.succeed { released.incrementAndGet(); () }) { _ =>
      ZIO.succeed { entered.success(()); () } *> ZIO.never
    }
    val execution = start(runner, effect)
    for {
      _ <- entered.future
      interrupted <- runner.unsafeRunAsyncAsFuture(execution.interruption.interrupt)
    } yield {
      assert(interrupted == Exit.Success(()))
      assert(released.get() == 1)
      println("ZIO_INTERRUPTED_EXECUTION_COMPLETION stopCompleted=true finalizers=" + released.get() + " executionCompleted=" + execution.completion.isCompleted)
      assert(execution.completion.isCompleted, "An interruption action must settle its execution completion after finalization")
      assert(execution.completion.value.exists(_.get.isInterrupted))
    }
  }
}
