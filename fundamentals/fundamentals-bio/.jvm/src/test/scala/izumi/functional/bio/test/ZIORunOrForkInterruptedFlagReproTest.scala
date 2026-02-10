package izumi.functional.bio.test

import org.scalatest.wordspec.AnyWordSpec
import zio.{FiberId, Runtime, Unsafe, ZIO}
import zio.durationInt

import java.util.concurrent.{CompletableFuture, TimeUnit, TimeoutException}

class ZIORunOrForkInterruptedFlagReproTest extends AnyWordSpec {
  private final val FlagProbeIterations = 200
  private final val PreWaitProbeRepetitions = 120

  "runOrFork interrupt handling" should {
    "clear pre-existing thread interrupt flag unlike direct fork path" in {
      val runtime = Runtime.default

      val runOrForkLost = (1 to FlagProbeIterations).count { idx =>
        observeFlagAfterRunOrFork(runtime, ZIO.sleep(1.day), s"runorfork-flag-$idx")
      }
      val directForkLost = (1 to FlagProbeIterations).count { idx =>
        observeFlagAfterFork(runtime, ZIO.sleep(1.day), s"directfork-flag-$idx")
      }

      assert(
        runOrForkLost > 0,
        s"runOrFork preserved interrupt flag unexpectedly for all $FlagProbeIterations iterations",
      )
      assert(directForkLost == 0, s"direct fork path lost interrupt flag $directForkLost times")
    }

    "sometimes block before reaching Left(fiber) wait setup when thread is pre-interrupted" in {
      val runtime = Runtime.default
      var blockedBeforeWait = 0

      (1 to PreWaitProbeRepetitions).foreach { iteration =>
        val enteredWait = new CompletableFuture[Unit]()
        val completed = new CompletableFuture[Either[Throwable, zio.Exit[Throwable, Unit]]]()

        val runner = new Thread(
          new Runnable {
            override def run(): Unit = {
              try {
                Thread.currentThread().interrupt()
                val exit = unsafeRunSyncViaRunOrFork(
                  runtime = runtime,
                  effect = ZIO.sleep(1.second).unit,
                  beforeWait = () => enteredWait.complete(()),
                )
                completed.complete(Right(exit))
              } catch {
                case t: Throwable =>
                  completed.complete(Left(t))
              }
              ()
            }
          },
          s"runorfork-repro-$iteration",
        )
        runner.start()

        val timedOutBeforeWait = try {
          enteredWait.get(200, TimeUnit.MILLISECONDS)
          false
        } catch {
          case _: TimeoutException => true
        }
        if (timedOutBeforeWait) {
          blockedBeforeWait += 1
        }

        try {
          val result = completed.get(5, TimeUnit.SECONDS)
          result match {
            case Right(_) => ()
            case Left(t) =>
              fail(s"iteration=$iteration unexpected throwable=${t.getClass.getName}: ${t.getMessage}")
          }
        } catch {
          case _: TimeoutException =>
            fail(s"iteration=$iteration timed out waiting for completion")
        } finally {
          runner.interrupt()
          runner.join(100)
        }
      }

      assert(
        blockedBeforeWait > 0,
        s"expected at least one pre-wait stall with pre-interrupted thread, observed 0/$PreWaitProbeRepetitions",
      )
    }
  }

  private def unsafeRunSyncViaRunOrFork[A](
    runtime: Runtime[Any],
    effect: ZIO[Any, Throwable, A],
    beforeWait: () => Unit,
  ): zio.Exit[Throwable, A] = {
    Unsafe.unsafe { implicit unsafe =>
      runtime.unsafe.runOrFork(effect) match {
        case Right(exit) =>
          exit

        case Left(fiber) =>
          val resultFuture = new CompletableFuture[zio.Exit[Throwable, A]]()
          fiber.unsafe.addObserver(exit => { resultFuture.complete(exit); () })
          beforeWait()
          var wasInterrupted = false

          try {
            resultFuture.get()
          } catch {
            case _: InterruptedException =>
              wasInterrupted = true
              import zio._izumicompat_.__ZIOOneShot.OneShot
              val interruptedOneShot = OneShot.make[zio.Exit[Nothing, zio.Exit[Throwable, A]]]
              val interruptionFiber = runtime.unsafe.fork(fiber.interruptAs(FiberId.None))
              interruptionFiber.unsafe.addObserver(interruptedOneShot.set)
              interruptedOneShot.get()
          }

          if (wasInterrupted) {
            Thread.currentThread().interrupt()
          }

          resultFuture.get()
      }
    }
  }

  private def observeFlagAfterRunOrFork(
    runtime: Runtime[Any],
    effect: ZIO[Any, Throwable, Any],
    name: String,
  ): Boolean = {
    val outcome = new CompletableFuture[Boolean]()
    val runner = new Thread(
      new Runnable {
        override def run(): Unit = {
          val lost = Unsafe.unsafe { implicit unsafe =>
            Thread.currentThread().interrupt()
            val before = Thread.currentThread().isInterrupted
            val after = runtime.unsafe.runOrFork(effect) match {
              case Right(_) =>
                Thread.currentThread().isInterrupted
              case Left(fiber) =>
                runtime.unsafe.fork(fiber.interruptAs(FiberId.None))
                Thread.currentThread().isInterrupted
            }
            if (Thread.currentThread().isInterrupted) {
              Thread.interrupted()
              ()
            }
            before && !after
          }
          outcome.complete(lost)
          ()
        }
      },
      name,
    )
    runner.start()
    val lost = outcome.get(5, TimeUnit.SECONDS)
    runner.join(100)
    lost
  }

  private def observeFlagAfterFork(
    runtime: Runtime[Any],
    effect: ZIO[Any, Throwable, Any],
    name: String,
  ): Boolean = {
    val outcome = new CompletableFuture[Boolean]()
    val runner = new Thread(
      new Runnable {
        override def run(): Unit = {
          val lost = Unsafe.unsafe { implicit unsafe =>
            Thread.currentThread().interrupt()
            val before = Thread.currentThread().isInterrupted
            val fiber = runtime.unsafe.fork(effect)
            runtime.unsafe.fork(fiber.interruptAs(FiberId.None))
            val after = Thread.currentThread().isInterrupted
            if (Thread.currentThread().isInterrupted) {
              Thread.interrupted()
              ()
            }
            before && !after
          }
          outcome.complete(lost)
          ()
        }
      },
      name,
    )
    runner.start()
    val lost = outcome.get(5, TimeUnit.SECONDS)
    runner.join(100)
    lost
  }
}
