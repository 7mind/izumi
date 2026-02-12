package zio.test

import izumi.functional.bio.UnsafeRun2
import org.scalatest.wordspec.AnyWordSpec
import zio.{FiberId, Runtime, Unsafe, ZIO, durationInt}

import java.util.concurrent.{CompletableFuture, CountDownLatch, TimeUnit, TimeoutException}

class ZIORunOrForkInterruptedFlagReproTest extends AnyWordSpec {
  private final val FlagProbeIterations = 200
  private final val VariantComparisonIterations = 40

  "runOrFork interrupt handling" should {
    "clear pre-existing thread interrupt flag unlike direct fork path" in {
      val runtime = Runtime.default

      val runOrForkLost = (1 to FlagProbeIterations).count {
        idx =>
          observeFlagAfterRunOrFork(runtime, ZIO.sleep(1.day), s"runorfork-flag-$idx")
      }
      val directForkLost = (1 to FlagProbeIterations).count {
        idx =>
          observeFlagAfterFork(runtime, ZIO.sleep(1.day), s"directfork-flag-$idx")
      }

      assert(
        runOrForkLost > 0,
        s"runOrFork preserved interrupt flag unexpectedly for all $FlagProbeIterations iterations",
      )
      assert(directForkLost == 0, s"direct fork path lost interrupt flag $directForkLost times")
    }

    // bullshit
//    "sometimes block before reaching Left(fiber) wait setup when thread is pre-interrupted" in {
//      val PreWaitProbeRepetitions = 120
//
//      val runtime = Runtime.default
//      var blockedBeforeWait = 0
//      var interruptsBeforeFork = 0
//      var interruptsAfterFork = 0
//
//      (1 to PreWaitProbeRepetitions).foreach {
//        iteration =>
//          val enteredWait = new CompletableFuture[Unit]()
//          val completed = new CompletableFuture[Either[Throwable, zio.Exit[Throwable, Unit]]]()
//
//          val runner = new Thread(
//            new Runnable {
//              override def run(): Unit = {
//                try {
//                  Thread.currentThread().interrupt()
//                  val exit = unsafeRunSyncViaRunOrFork(
//                    runtime = runtime,
//                    effect = ZIO.sleep(1.second).unit,
//                    afterRunOrFork = ex => {
//                      if (ex.isLeft) interruptsAfterFork += 1 else interruptsBeforeFork += 1
//                      enteredWait.complete(()).discard()
//                    },
//                  )
//                  completed.complete(Right(exit))
//                } catch {
//                  case t: Throwable =>
//                    completed.complete(Left(t))
//                }
//                ()
//              }
//            },
//            s"runorfork-repro-$iteration",
//          )
//          runner.start()
//
//          val timedOutBeforeWait =
//            try {
//              enteredWait.get(200, TimeUnit.MILLISECONDS)
//              false
//            } catch {
//              case _: TimeoutException => true
//            }
//          if (timedOutBeforeWait) {
//            blockedBeforeWait += 1
//          }
//
//          try {
//            val result = completed.get(5, TimeUnit.SECONDS)
//            result match {
//              case Right(_) => ()
//              case Left(t) =>
//                fail(s"iteration=$iteration unexpected throwable=${t.getClass.getName}: ${t.getMessage}")
//            }
//          } catch {
//            case _: TimeoutException =>
//              fail(s"iteration=$iteration timed out waiting for completion")
//          } finally {
//            runner.interrupt()
//            runner.join(100)
//          }
//      }
//
//      assert(
//        blockedBeforeWait > 0,
//        s"expected at least one pre-wait stall with pre-interrupted thread, observed 0/$PreWaitProbeRepetitions afterFork=$interruptsAfterFork beforeFork=$interruptsBeforeFork",
//      )
//    }

    "show identical observable behavior for v_badFork and v_good, and report comparison against runOrFork bridge" in {
      val runner = UnsafeRun2.createZIO[Any]().asInstanceOf[UnsafeRun2.ZIORunner[Any]]
      val runtime = Runtime.default
      runner.runtime // force runtime initialization on non-interrupted test thread
      val effect = ZIO.sleep(300.millis).unit
      val badCounts = scala.collection.mutable.Map.empty[VariantOutcome, Int].withDefaultValue(0)
      val runOrForkCounts = scala.collection.mutable.Map.empty[VariantOutcome, Int].withDefaultValue(0)
      var sameAsRunOrFork = 0

      (1 to VariantComparisonIterations).foreach {
        iteration =>
          val bad = runVariantOnce(
            name = s"variant-badfork-$iteration",
            effect = effect,
            invoke = io => runner.v_badFork(io),
          )
          val good = runVariantOnce(
            name = s"variant-good-$iteration",
            effect = effect,
            invoke = io => runner.v_good(io),
          )

          assert(
            bad == good,
            s"iteration=$iteration divergent outcomes: v_badFork=$bad v_good=$good",
          )

          badCounts.update(bad, badCounts(bad) + 1)

          val runOrFork = runRunOrForkVariantOnce(
            name = s"variant-runorfork-$iteration",
            runtime = runtime,
            effect = effect,
          )
          runOrForkCounts.update(runOrFork, runOrForkCounts(runOrFork) + 1)
          if (bad == runOrFork) {
            sameAsRunOrFork += 1
          }
      }

      println(s"[VariantCompare] iterations=$VariantComparisonIterations")
      println(s"[VariantCompare] v_badFork_v_good_counts=$badCounts")
      println(s"[VariantCompare] runOrFork_counts=$runOrForkCounts")
      println(s"[VariantCompare] sameAsRunOrFork=$sameAsRunOrFork")
      assert(runOrForkCounts.head._2 < badCounts.head._2)
    }

    "reproduce divergence under simple post-start external interrupts in this isolated harness" in {
      val PostStartInterruptIterations = 600

      val runner = UnsafeRun2.createZIO[Any]().asInstanceOf[UnsafeRun2.ZIORunner[Any]]
      runner.runtime // force runtime initialization on non-interrupted test thread
      val effect = ZIO.sleep(3.seconds).unit

      var goodMissedPrimaryInterrupt = 0
      var runOrForkMissedPrimaryInterrupt = 0

      (1 to PostStartInterruptIterations).foreach {
        iteration =>
          val good = runVariantWithPostStartInterrupt(
            name = s"poststart-good-$iteration",
            effect = effect,
            invoke = io => runner.v_badFork(io),
          )
          val runOrFork = runVariantWithPostStartInterrupt(
            name = s"poststart-runorfork-$iteration",
            effect = effect,
            invoke = io => runner.v_badRunOrFork(io),
          )

          if (good.primaryTimedOut) {
            goodMissedPrimaryInterrupt += 1
          }
          if (runOrFork.primaryTimedOut) {
            runOrForkMissedPrimaryInterrupt += 1
          }
      }

      println(s"[PostStartInterrupt] iterations=$PostStartInterruptIterations")
      println(s"[PostStartInterrupt] goodMissedPrimaryInterrupt=$goodMissedPrimaryInterrupt")
      println(s"[PostStartInterrupt] runOrForkMissedPrimaryInterrupt=$runOrForkMissedPrimaryInterrupt")
      assert(
        runOrForkMissedPrimaryInterrupt != 0 || goodMissedPrimaryInterrupt != 0,
        s"isolated post-start test observed divergence, runOrFork=$runOrForkMissedPrimaryInterrupt good=$goodMissedPrimaryInterrupt",
      )
    }
  }

  private def unsafeRunSyncViaRunOrFork[A](
    runtime: Runtime[Any],
    effect: ZIO[Any, Throwable, A],
    afterRunOrFork: Either[zio.Fiber[Any, Any], zio.Exit[Any, Any]] => Unit,
  ): zio.Exit[Throwable, A] = {
    Unsafe.unsafe {
      implicit unsafe =>
        runtime.unsafe.runOrFork(effect) match {
          case Right(exit) =>
            afterRunOrFork(Right(exit))
            exit

          case Left(fiber) =>
            val resultFuture = new CompletableFuture[zio.Exit[Throwable, A]]()
            fiber.unsafe.addObserver(exit => { resultFuture.complete(exit); () })
            afterRunOrFork(Left(fiber))
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
          val lost = Unsafe.unsafe {
            implicit unsafe =>
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
          val lost = Unsafe.unsafe {
            implicit unsafe =>
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

  private sealed trait VariantOutcome
  private object VariantOutcome {
    case object CompletedSuccess extends VariantOutcome
    case object CompletedFailure extends VariantOutcome
    case class Threw(className: String) extends VariantOutcome
    case object TimedOut extends VariantOutcome
  }

  private def runVariantOnce(
    name: String,
    effect: ZIO[Any, Throwable, Unit],
    invoke: ZIO[Any, Throwable, Unit] => izumi.functional.bio.Exit[Throwable, Unit],
  ): VariantOutcome = {
    val outcome = new CompletableFuture[Either[Throwable, izumi.functional.bio.Exit[Throwable, Unit]]]()
    val runner = new Thread(
      new Runnable {
        override def run(): Unit = {
          try {
            Thread.currentThread().interrupt()
            val exit = invoke(effect)
            outcome.complete(Right(exit))
          } catch {
            case t: Throwable =>
              outcome.complete(Left(t))
          }
          ()
        }
      },
      name,
    )

    runner.start()
    val result =
      try {
        outcome.get(5, TimeUnit.SECONDS) match {
          case Right(izumi.functional.bio.Exit.Success(_)) => VariantOutcome.CompletedSuccess
          case Right(_: izumi.functional.bio.Exit.Failure[_]) => VariantOutcome.CompletedFailure
          case Left(t) => VariantOutcome.Threw(t.getClass.getName)
        }
      } catch {
        case _: TimeoutException =>
          VariantOutcome.TimedOut
      } finally {
        runner.interrupt()
        runner.join(100)
      }

    result
  }

  private def runRunOrForkVariantOnce(
    name: String,
    runtime: Runtime[Any],
    effect: ZIO[Any, Throwable, Unit],
  ): VariantOutcome = {
    val outcome = new CompletableFuture[Either[Throwable, zio.Exit[Throwable, Unit]]]()
    val runner = new Thread(
      new Runnable {
        override def run(): Unit = {
          try {
            Thread.currentThread().interrupt()
            val exit = unsafeRunSyncViaRunOrFork(
              runtime = runtime,
              effect = effect,
              afterRunOrFork = _ => (),
            )
            outcome.complete(Right(exit))
          } catch {
            case t: Throwable =>
              outcome.complete(Left(t))
          }
          ()
        }
      },
      name,
    )

    runner.start()
    val result =
      try {
        outcome.get(5, TimeUnit.SECONDS) match {
          case Right(zio.Exit.Success(_)) => VariantOutcome.CompletedSuccess
          case Right(zio.Exit.Failure(_)) => VariantOutcome.CompletedFailure
          case Left(t) => VariantOutcome.Threw(t.getClass.getName)
        }
      } catch {
        case _: TimeoutException =>
          VariantOutcome.TimedOut
      } finally {
        runner.interrupt()
        runner.join(100)
      }

    result
  }

  private case class PostStartInterruptOutcome(primaryTimedOut: Boolean)

  private def runVariantWithPostStartInterrupt(
    name: String,
    effect: ZIO[Any, Throwable, Unit],
    invoke: ZIO[Any, Throwable, Unit] => izumi.functional.bio.Exit[Throwable, Unit],
  ): PostStartInterruptOutcome = {
    val latch = new CountDownLatch(1)

    val outcome = new CompletableFuture[Either[Throwable, izumi.functional.bio.Exit[Throwable, Unit]]]()
    val worker = new Thread(
      new Runnable {
        override def run(): Unit = {
          try {
            val exit = invoke(ZIO.uninterruptibleMask(restore => ZIO.attempt(latch.countDown()) *> restore(effect)))
            outcome.complete(Right(exit))
          } catch {
            case t: Throwable =>
              outcome.complete(Left(t))
          }
          ()
        }
      },
      name,
    )

    worker.start()
    latch.await()
    worker.interrupt()

    val primaryTimedOut =
      try {
        outcome.get(500, TimeUnit.MILLISECONDS)
        false
      } catch {
        case _: TimeoutException =>
          true
      } finally {
        if (!outcome.isDone) {
          worker.interrupt()
        }
        try {
          outcome.get(5, TimeUnit.SECONDS)
        } catch {
          case _: TimeoutException => ()
        }
        worker.join(100)
      }

    PostStartInterruptOutcome(primaryTimedOut)
  }
}
