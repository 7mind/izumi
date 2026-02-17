package zio.test

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import cats.syntax.all.*
import org.scalatest.wordspec.AnyWordSpec
import zio.{FiberId, Runtime, Unsafe, ZIO, durationInt}

import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.{CompletableFuture, ConcurrentHashMap, CountDownLatch, Executors, TimeUnit}
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}

class ZIORunOrForkInterruptedFlagReproTest extends AnyWordSpec {
  private final val Attempts = sys.props.get("izumi.repro.attempts").flatMap(_.toIntOption).getOrElse(200)
  private final val AllEffectsAttempts = sys.props.get("izumi.repro.allEffectsAttempts").flatMap(_.toIntOption).getOrElse(2000)
  private final val Envs = 3
  private final val SuitesPerEnv = 3
  private final val TestsPerSuite = 5
  private final val TotalSuites = Envs * SuitesPerEnv
  private final val TotalTests = TotalSuites * TestsPerSuite
  private final val TotalEffects = 3
  private final val TotalTestsAllEffects = SuitesPerEnv * TestsPerSuite * TotalEffects

  private final val StartedTimeoutSeconds = 10
  private final val RunnerJoinMillis = 20000L
  private final val ObservationMillis = 20000L
  private final val BadStyle = RunnerStyle.fromProperty("izumi.repro.badStyle", RunnerStyle.RunOrForkNoAwait)
  private final val GoodStyle = RunnerStyle.fromProperty("izumi.repro.goodStyle", RunnerStyle.ForkWaitInterrupt)

  "runOrFork layered interruption repro" should {
    "show divergence between fork-without-await and fork-with-await under blocking RunnerToF-style layering" in {
      val badResults = (1 to Attempts).map(runAttempt(_, BadStyle))
      val goodResults = (1 to Attempts).map(runAttempt(_, GoodStyle))

      val badReproduced = badResults.filter(_.notInterruptedCount > 0)
      val badFailedStarts = badResults.count(!_.allStarted)
      val badFailedStops = badResults.count(!_.allStoppedObserved)
      val badRunnerHung = badResults.count(_.runnerStillAlive)
      val badTotalInterrupted = badResults.map(_.interruptedCount).sum
      val badTotalNotInterrupted = badResults.map(_.notInterruptedCount).sum

      val goodFailedStarts = goodResults.count(!_.allStarted)
      val goodFailedStops = goodResults.count(!_.allStoppedObserved)
      val goodRunnerHung = goodResults.count(_.runnerStillAlive)
      val goodTotalInterrupted = goodResults.map(_.interruptedCount).sum
      val goodTotalNotInterrupted = goodResults.map(_.notInterruptedCount).sum

      println(s"[LayeredRepro][bad] style=${BadStyle.name} attempts=$Attempts")
      println(s"[LayeredRepro][bad] reproducedAttempts=${badReproduced.size}")
      println(s"[LayeredRepro][bad] failedStarts=$badFailedStarts")
      println(s"[LayeredRepro][bad] failedStops=$badFailedStops")
      println(s"[LayeredRepro][bad] runnerHung=$badRunnerHung")
      println(s"[LayeredRepro][bad] totalInterrupted=$badTotalInterrupted")
      println(s"[LayeredRepro][bad] totalNotInterrupted=$badTotalNotInterrupted")
      badReproduced.take(10).foreach {
        r =>
          println(
            s"[LayeredRepro][bad][reproduced] attempt=${r.attempt} started=${r.allStarted} runnerAlive=${r.runnerStillAlive} interrupted=${r.interruptedCount} notInterrupted=${r.notInterruptedCount} stoppedObserved=${r.allStoppedObserved} runnerOutcome=${r.runnerOutcome}"
          )
      }

      println(s"[LayeredRepro][good] style=${GoodStyle.name} attempts=$Attempts")
      println(s"[LayeredRepro][good] failedStarts=$goodFailedStarts")
      println(s"[LayeredRepro][good] failedStops=$goodFailedStops")
      println(s"[LayeredRepro][good] runnerHung=$goodRunnerHung")
      println(s"[LayeredRepro][good] totalInterrupted=$goodTotalInterrupted")
      println(s"[LayeredRepro][good] totalNotInterrupted=$goodTotalNotInterrupted")

      // we still fail to reproduce non-interruption
      // BAD!
      assert(badReproduced.isEmpty)
      assert(goodTotalNotInterrupted == 0)
      // end BAD!

      assert(badFailedStarts == 0, s"fork-no-await: some attempts did not start all tests: failedStarts=$badFailedStarts")
      assert(badFailedStops == 0, s"fork-no-await: some attempts did not stop all tests in time: failedStops=$badFailedStops")
      assert(badRunnerHung == 0, s"fork-no-await: runner thread remained alive in some attempts: runnerHung=$badRunnerHung")
      assert(goodFailedStarts == 0, s"fork runner: some attempts did not start all tests: failedStarts=$goodFailedStarts")
      assert(goodFailedStops == 0, s"fork runner: some attempts did not stop all tests in time: failedStops=$goodFailedStops")
      assert(goodRunnerHung == 0, s"fork runner: runner thread remained alive in some attempts: runnerHung=$goodRunnerHung")
      assert(goodTotalNotInterrupted == 0, s"fork runner had uninterruptible completions: totalNotInterrupted=$goodTotalNotInterrupted")
    }

    "replicate all-effects blocking topology and search for zio non-interruption" in {
      val badResults = (1 to AllEffectsAttempts).map(runAllEffectsAttempt(_, BadStyle))
      val goodResults = (1 to AllEffectsAttempts).map(runAllEffectsAttempt(_, GoodStyle))

      val badZioNotInterrupted = badResults.map(_.zioNotInterruptedCount).sum
      val goodZioNotInterrupted = goodResults.map(_.zioNotInterruptedCount).sum

      val badFailedStarts = badResults.count(!_.allStarted)
      val goodFailedStarts = goodResults.count(!_.allStarted)

      val badFailedStops = badResults.count(!_.allStoppedObserved)
      val goodFailedStops = goodResults.count(!_.allStoppedObserved)

      val badRunnerHung = badResults.count(_.runnerStillAlive)
      val goodRunnerHung = goodResults.count(_.runnerStillAlive)

      val badReproducedAttempts = badResults.count(_.zioNotInterruptedCount > 0)
      val goodReproducedAttempts = goodResults.count(_.zioNotInterruptedCount > 0)

      println(s"[AllEffectsRepro][bad] style=${BadStyle.name} attempts=$AllEffectsAttempts")
      println(s"[AllEffectsRepro][bad] reproducedAttempts=$badReproducedAttempts")
      println(s"[AllEffectsRepro][bad] zioNotInterruptedTotal=$badZioNotInterrupted")
      println(s"[AllEffectsRepro][bad] failedStarts=$badFailedStarts failedStops=$badFailedStops runnerHung=$badRunnerHung")
      println(s"[AllEffectsRepro][good] style=${GoodStyle.name} attempts=$AllEffectsAttempts")
      println(s"[AllEffectsRepro][good] reproducedAttempts=$goodReproducedAttempts")
      println(s"[AllEffectsRepro][good] zioNotInterruptedTotal=$goodZioNotInterrupted")
      println(s"[AllEffectsRepro][good] failedStarts=$goodFailedStarts failedStops=$goodFailedStops runnerHung=$goodRunnerHung")

      // we still fail to reproduce non-interruption
      // BAD!
      (badResults ++ goodResults).foreach {
        x =>
          x.productIterator.zip(x.productElementNames).foreach {
            case (i: Int, s) =>
              val ignored = Set("zioInterruptedCount", "catsInterruptedCount", "identityInterruptedCount", "attempt")
              if (!ignored(s)) {
                assert(i == 0, s"$s not 0")
              }
            case _ =>
          }
      }
      // end BAD!

      badResults.filter(_.zioNotInterruptedCount > 0).take(5).foreach {
        r =>
          println(
            s"[AllEffectsRepro][bad][reproduced] attempt=${r.attempt} zioInterrupted=${r.zioInterruptedCount} zioNotInterrupted=${r.zioNotInterruptedCount} catsInterrupted=${r.catsInterruptedCount} catsNotInterrupted=${r.catsNotInterruptedCount} identityInterrupted=${r.identityInterruptedCount} identityNotInterrupted=${r.identityNotInterruptedCount} runnerOutcome=${r.runnerOutcome}"
          )
      }

      assert(badFailedStarts == 0, s"all-effects bad: some attempts did not start all tests: failedStarts=$badFailedStarts")
      assert(goodFailedStarts == 0, s"all-effects good: some attempts did not start all tests: failedStarts=$goodFailedStarts")
      assert(badFailedStops == 0, s"all-effects bad: some attempts did not stop all tests in time: failedStops=$badFailedStops")
      assert(goodFailedStops == 0, s"all-effects good: some attempts did not stop all tests in time: failedStops=$goodFailedStops")
      assert(badRunnerHung == 0, s"all-effects bad: runner thread remained alive: runnerHung=$badRunnerHung")
      assert(goodRunnerHung == 0, s"all-effects good: runner thread remained alive: runnerHung=$goodRunnerHung")
    }

    "complete one layered attempt without deadlock" in {
      val result = runAttempt(0, RunnerStyle.RuntimeRun)
      assert(result.allStarted, s"single attempt did not start all tests: $result")
      assert(!result.runnerStillAlive, s"single attempt left runner alive: $result")
    }
  }

  private case class AttemptState(
    startedLatch: CountDownLatch,
    stoppedLatch: CountDownLatch,
    interruptedCounter: AtomicInteger,
    notInterruptedCounter: AtomicInteger,
  )

  private case class AttemptResult(
    attempt: Int,
    style: String,
    allStarted: Boolean,
    allStoppedObserved: Boolean,
    runnerStillAlive: Boolean,
    interruptedCount: Int,
    notInterruptedCount: Int,
    runnerOutcome: String,
  )

  private case class AllEffectsAttemptState(
    startedLatch: CountDownLatch,
    stoppedLatch: CountDownLatch,
    zioInterruptedCounter: AtomicInteger,
    zioNotInterruptedCounter: AtomicInteger,
    catsInterruptedCounter: AtomicInteger,
    catsNotInterruptedCounter: AtomicInteger,
    identityInterruptedCounter: AtomicInteger,
    identityNotInterruptedCounter: AtomicInteger,
  )

  private case class AllEffectsAttemptResult(
    attempt: Int,
    style: String,
    allStarted: Boolean,
    allStoppedObserved: Boolean,
    runnerStillAlive: Boolean,
    zioInterruptedCount: Int,
    zioNotInterruptedCount: Int,
    catsInterruptedCount: Int,
    catsNotInterruptedCount: Int,
    identityInterruptedCount: Int,
    identityNotInterruptedCount: Int,
    runnerOutcome: String,
  )

  private final class IdentityParallelRunner {
    private val ec = SharedIdentityEC.executionContext

    def parTraverse_[A](all: Iterable[A])(f: A => Unit): Unit = {
      val runningThreads = ConcurrentHashMap.newKeySet[Thread]()
      val futures = all.iterator.map {
        a =>
          Future {
            val thread = Thread.currentThread()
            runningThreads.add(thread)
            try {
              f(a)
            } finally {
              runningThreads.remove(thread)
              ()
            }
          }(using ec)
      }.toList

      try {
        implicit val ec0: ExecutionContext = ec
        val _ = Await.result(Future.sequence(futures), Duration.Inf)
      } catch {
        case t: InterruptedException =>
          runningThreads.forEach(_.interrupt())
          throw t
      }
    }

    def shutdown(): Unit = {
      ()
    }
  }

  private object SharedIdentityEC {
    private val executor = Executors.newCachedThreadPool(
      (r: Runnable) => {
        val t = new Thread(r, s"identity-shared-${UUID.randomUUID()}")
        t.setDaemon(true)
        t
      }
    )
    val executionContext: ExecutionContext = ExecutionContext.fromExecutorService(executor)
  }

  private sealed trait RunnerStyle {
    def key: String
    def name: String
    def runSync[E, A](runtime: Runtime[Any], effect: ZIO[Any, E, A]): zio.Exit[E, A]
  }

  private object RunnerStyle {
    case object RuntimeRun extends RunnerStyle {
      override val key: String = "runtimeRun"
      override val name: String = "runtime.unsafe.run"
      override def runSync[E, A](runtime: Runtime[Any], effect: ZIO[Any, E, A]): zio.Exit[E, A] = {
        Unsafe.unsafe {
          implicit unsafe =>
            runtime.unsafe.run(effect)
        }
      }
    }

    case object RunOrForkNoAwait extends RunnerStyle {
      override val key: String = "runOrForkNoAwait"
      override val name: String = "runtime.unsafe.runOrFork + no interruption await"
      override def runSync[E, A](runtime: Runtime[Any], effect: ZIO[Any, E, A]): zio.Exit[E, A] = {
        Unsafe.unsafe {
          implicit unsafe =>
            runtime.unsafe.runOrFork(effect) match {
              case Right(exit) =>
                exit

              case Left(fiber) =>
                val resultFuture = new CompletableFuture[zio.Exit[E, A]]()
                fiber.unsafe.addObserver(exit => { resultFuture.complete(exit); () })
                var wasInterrupted = false
                try {
                  resultFuture.get()
                } catch {
                  case _: InterruptedException =>
                    wasInterrupted = true
                    runtime.unsafe.fork(fiber.interruptAs(FiberId.None))
                }
                if (wasInterrupted) {
                  Thread.currentThread().interrupt()
                }
                resultFuture.get()
            }
        }
      }
    }

    case object ForkNoAwaitInterrupt extends RunnerStyle {
      override val key: String = "forkNoAwaitInterrupt"
      override val name: String = "runtime.unsafe.fork + no interruption await"
      override def runSync[E, A](runtime: Runtime[Any], effect: ZIO[Any, E, A]): zio.Exit[E, A] = {
        Unsafe.unsafe {
          implicit unsafe =>
            val fiber = runtime.unsafe.fork(effect)
            val resultFuture = new CompletableFuture[zio.Exit[E, A]]()
            fiber.unsafe.addObserver(exit => { resultFuture.complete(exit); () })
            var wasInterrupted = false
            try {
              resultFuture.get()
            } catch {
              case _: InterruptedException =>
                wasInterrupted = true
                runtime.unsafe.fork(fiber.interruptAs(FiberId.None))
            }
            if (wasInterrupted) {
              Thread.currentThread().interrupt()
            }
            resultFuture.get()
        }
      }
    }

    case object ForkWaitInterrupt extends RunnerStyle {
      override val key: String = "forkWaitInterrupt"
      override val name: String = "runtime.unsafe.fork + await interruption"
      override def runSync[E, A](runtime: Runtime[Any], effect: ZIO[Any, E, A]): zio.Exit[E, A] = {
        Unsafe.unsafe {
          implicit unsafe =>
            val fiber = runtime.unsafe.fork(effect)
            val resultFuture = new CompletableFuture[zio.Exit[E, A]]()
            fiber.unsafe.addObserver(exit => { resultFuture.complete(exit); () })
            var wasInterrupted = false
            try {
              resultFuture.get()
            } catch {
              case _: InterruptedException =>
                wasInterrupted = true
                import zio._izumicompat_.__ZIOOneShot.OneShot
                val interruptedOneShot = OneShot.make[zio.Exit[Nothing, zio.Exit[E, A]]]
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

    private final val All: List[RunnerStyle] = List(RuntimeRun, RunOrForkNoAwait, ForkNoAwaitInterrupt, ForkWaitInterrupt)

    def fromProperty(property: String, default: RunnerStyle): RunnerStyle = {
      sys.props.get(property).fold(default) {
        raw =>
          All.find(_.key == raw).getOrElse {
            throw new IllegalArgumentException(s"Invalid style '$raw' for $property. Expected one of: ${All.map(_.key).mkString(", ")}")
          }
      }
    }
  }

  private def runAllEffectsAttempt(attempt: Int, style: RunnerStyle): AllEffectsAttemptResult = {
    val outerRuntime = Runtime.default
    val zioInnerRuntime = Runtime.default
    val identityPar = new IdentityParallelRunner

    val state = AllEffectsAttemptState(
      startedLatch = new CountDownLatch(TotalTestsAllEffects),
      stoppedLatch = new CountDownLatch(TotalTestsAllEffects),
      zioInterruptedCounter = new AtomicInteger(0),
      zioNotInterruptedCounter = new AtomicInteger(0),
      catsInterruptedCounter = new AtomicInteger(0),
      catsNotInterruptedCounter = new AtomicInteger(0),
      identityInterruptedCounter = new AtomicInteger(0),
      identityNotInterruptedCounter = new AtomicInteger(0),
    )

    val runnerResult = new CompletableFuture[Either[Throwable, zio.Exit[Throwable, Unit]]]()

    val runnerThread = new Thread(
      new Runnable {
        override def run(): Unit = {
          try {
            val exit = style.runSync(
              runtime = outerRuntime,
              effect = layeredProgramAllEffects(attempt, state, zioInnerRuntime, identityPar, style),
            )
            runnerResult.complete(Right(exit))
          } catch {
            case t: Throwable =>
              runnerResult.complete(Left(t))
          }
          ()
        }
      },
      s"all-effects-outer-runner-$attempt",
    )

    try {
      runnerThread.start()
      val allStarted = state.startedLatch.await(StartedTimeoutSeconds, TimeUnit.SECONDS)

      runnerThread.interrupt()

      runnerThread.join(RunnerJoinMillis)
      val runnerStillAlive = runnerThread.isAlive

      val allStoppedObserved = state.stoppedLatch.await(ObservationMillis, TimeUnit.MILLISECONDS)

      val runnerOutcome =
        if (!runnerResult.isDone) {
          if (runnerStillAlive) "no-result-runner-alive" else "no-result-runner-finished"
        } else {
          runnerResult.getNow(null.asInstanceOf[Either[Throwable, zio.Exit[Throwable, Unit]]]) match {
            case Right(zio.Exit.Success(_)) => "success"
            case Right(zio.Exit.Failure(_)) => "failure"
            case Left(t) => s"threw:${t.getClass.getSimpleName}"
          }
        }

      AllEffectsAttemptResult(
        attempt = attempt,
        style = style.name,
        allStarted = allStarted,
        allStoppedObserved = allStoppedObserved,
        runnerStillAlive = runnerStillAlive,
        zioInterruptedCount = state.zioInterruptedCounter.get(),
        zioNotInterruptedCount = state.zioNotInterruptedCounter.get(),
        catsInterruptedCount = state.catsInterruptedCounter.get(),
        catsNotInterruptedCount = state.catsNotInterruptedCounter.get(),
        identityInterruptedCount = state.identityInterruptedCounter.get(),
        identityNotInterruptedCount = state.identityNotInterruptedCounter.get(),
        runnerOutcome = runnerOutcome,
      )
    } finally {
      identityPar.shutdown()
    }
  }

  private def layeredProgramAllEffects(
    attempt: Int,
    state: AllEffectsAttemptState,
    zioRuntime: Runtime[Any],
    identityPar: IdentityParallelRunner,
    style: RunnerStyle,
  ): ZIO[Any, Throwable, Unit] = {
    val zioEnv = runEnvViaBlockingRunnerToF(
      runtime = zioRuntime,
      envEffect = zioEnvProgram(attempt, state),
      style = style,
    )

    val catsEnv = runCatsEnvViaBlockingRunnerToF(catsEnvProgram(attempt, state), IORuntime.global)
    val identityEnv = runIdentityEnvViaBlockingRunnerToF(identityEnvProgram(attempt, state, identityPar))

    ZIO.collectAllParDiscard(List(zioEnv, catsEnv, identityEnv))
  }

  private def zioEnvProgram(
    attempt: Int,
    state: AllEffectsAttemptState,
  ): ZIO[Any, Throwable, Unit] = {
    ZIO.foreachParDiscard(0 until SuitesPerEnv) {
      suiteId =>
        ZIO.foreachParDiscard(0 until TestsPerSuite) {
          testId =>
            val duration = (10 + testId).seconds
            ZIO.acquireReleaseWith(ZIO.unit)(_ => ZIO.succeed(state.stoppedLatch.countDown())) {
              _ =>
                (ZIO.succeed(state.startedLatch.countDown()) *>
                ZIO.sleep(duration).onInterrupt(ZIO.succeed(state.zioInterruptedCounter.incrementAndGet()).unit)) *>
                ZIO.succeed {
                  val thread = Thread.currentThread()
                  val value = state.zioNotInterruptedCounter.incrementAndGet()
                  println(
                    s"[AllEffectsRepro][zio][signalNotInterrupted] attempt=$attempt suite=$suiteId test=$testId total=$value thread=${thread.getName}:${thread.getId}"
                  )
                }
            }
        }
    }
  }

  private def catsEnvProgram(
    attempt: Int,
    state: AllEffectsAttemptState,
  ): IO[Unit] = {
    (0 until SuitesPerEnv).toList.parTraverse_ {
      suiteId =>
        (0 until TestsPerSuite).toList.parTraverse_ {
          testId =>
            val duration = scala.concurrent.duration.DurationInt(10 + testId).seconds
            val io =
              (IO.delay(state.startedLatch.countDown()) *>
              IO.sleep(duration).onCancel(IO.delay(state.catsInterruptedCounter.incrementAndGet()).void)) *>
              IO.delay {
                val thread = Thread.currentThread()
                val value = state.catsNotInterruptedCounter.incrementAndGet()
                println(
                  s"[AllEffectsRepro][cats][signalNotInterrupted] attempt=$attempt suite=$suiteId test=$testId total=$value thread=${thread.getName}:${thread.getId}"
                )
              }
            io.guarantee(IO.delay(state.stoppedLatch.countDown()))
        }
    }
  }

  private def identityEnvProgram(
    attempt: Int,
    state: AllEffectsAttemptState,
    identityPar: IdentityParallelRunner,
  ): Unit = {
    identityPar.parTraverse_(0 until SuitesPerEnv) {
      suiteId =>
        identityPar.parTraverse_(0 until TestsPerSuite) {
          testId =>
            val durationMillis = scala.concurrent.duration.DurationInt(10 + testId).seconds.toMillis
            state.startedLatch.countDown()
            try {
              Thread.sleep(durationMillis)
              val thread = Thread.currentThread()
              val value = state.identityNotInterruptedCounter.incrementAndGet()
              println(
                s"[AllEffectsRepro][identity][signalNotInterrupted] attempt=$attempt suite=$suiteId test=$testId total=$value thread=${thread.getName}:${thread.getId}"
              )
            } catch {
              case ex: InterruptedException =>
                state.identityInterruptedCounter.incrementAndGet()
                throw ex
            } finally {
              state.stoppedLatch.countDown()
              ()
            }
        }
    }
  }

  private def runCatsEnvViaBlockingRunnerToF(
    envEffect: IO[Unit],
    runtime: IORuntime,
  ): ZIO[Any, Throwable, Unit] = {
    ZIO.attemptBlockingInterrupt {
      scala.concurrent.blocking {
        envEffect.unsafeRunSync()(using runtime)
      }
    }
  }

  private def runIdentityEnvViaBlockingRunnerToF(
    envEffect: => Unit
  ): ZIO[Any, Throwable, Unit] = {
    ZIO.attemptBlockingInterrupt {
      scala.concurrent.blocking {
        envEffect
      }
    }
  }

  private def runAttempt(attempt: Int, style: RunnerStyle): AttemptResult = {
    val outerRuntime = Runtime.default
    val innerRuntimes = Array.fill(Envs)(Runtime.default)

    val state = AttemptState(
      startedLatch = new CountDownLatch(TotalTests),
      stoppedLatch = new CountDownLatch(TotalTests),
      interruptedCounter = new AtomicInteger(0),
      notInterruptedCounter = new AtomicInteger(0),
    )

    val runnerResult = new CompletableFuture[Either[Throwable, zio.Exit[Throwable, Unit]]]()

    val runnerThread = new Thread(
      new Runnable {
        override def run(): Unit = {
          try {
            val exit = style.runSync(
              runtime = outerRuntime,
              effect = layeredProgram(attempt, state, innerRuntimes, style),
            )
            runnerResult.complete(Right(exit))
          } catch {
            case t: Throwable =>
              runnerResult.complete(Left(t))
          }
          ()
        }
      },
      s"outer-runner-$attempt",
    )

    runnerThread.start()
    val allStarted = state.startedLatch.await(StartedTimeoutSeconds, TimeUnit.SECONDS)

    runnerThread.interrupt()

    runnerThread.join(RunnerJoinMillis)
    val runnerStillAlive = runnerThread.isAlive

    val allStoppedObserved = state.stoppedLatch.await(ObservationMillis, TimeUnit.MILLISECONDS)

    val runnerOutcome =
      if (!runnerResult.isDone) {
        if (runnerStillAlive) "no-result-runner-alive" else "no-result-runner-finished"
      } else {
        val result = runnerResult.getNow(null.asInstanceOf[Either[Throwable, zio.Exit[Throwable, Unit]]])
        result match {
          case Right(zio.Exit.Success(_)) =>
            "success"
          case Right(zio.Exit.Failure(_)) =>
            "failure"
          case Left(t) =>
            s"threw:${t.getClass.getSimpleName}"
        }
      }

    AttemptResult(
      attempt = attempt,
      style = style.name,
      allStarted = allStarted,
      allStoppedObserved = allStoppedObserved,
      runnerStillAlive = runnerStillAlive,
      interruptedCount = state.interruptedCounter.get(),
      notInterruptedCount = state.notInterruptedCounter.get(),
      runnerOutcome = runnerOutcome,
    )
  }

  private def layeredProgram(
    attempt: Int,
    state: AttemptState,
    innerRuntimes: Array[Runtime[Any]],
    style: RunnerStyle,
  ): ZIO[Any, Throwable, Unit] = {
    ZIO.foreachParDiscard(0 until Envs) {
      envId =>
        val envRuntime = innerRuntimes(envId)
        runEnvViaBlockingRunnerToF(
          runtime = envRuntime,
          envEffect = envProgram(attempt, envId, state),
          style = style,
        )
    }
  }

  private def envProgram(
    attempt: Int,
    envId: Int,
    state: AttemptState,
  ): ZIO[Any, Throwable, Unit] = {
    ZIO.foreachParDiscard(0 until SuitesPerEnv) {
      suiteIdInEnv =>
        val suiteId = envId * SuitesPerEnv + suiteIdInEnv
        suiteProgram(attempt, envId, suiteId, state)
    }
  }

  private def suiteProgram(
    attempt: Int,
    envId: Int,
    suiteId: Int,
    state: AttemptState,
  ): ZIO[Any, Throwable, Unit] = {
    ZIO.foreachParDiscard(0 until TestsPerSuite) {
      testId =>
        singleSleepTest(attempt, envId, suiteId, testId, state)
    }
  }

  private def singleSleepTest(
    attempt: Int,
    envId: Int,
    suiteId: Int,
    testId: Int,
    state: AttemptState,
  ): ZIO[Any, Throwable, Unit] = {
    val duration = (10 + testId).seconds

    ZIO.acquireReleaseWith(ZIO.unit)(_ => ZIO.succeed(state.stoppedLatch.countDown())) {
      _ =>
        (ZIO.succeed(state.startedLatch.countDown()) *>
        ZIO.sleep(duration).onInterrupt(ZIO.succeed(state.interruptedCounter.incrementAndGet()).unit)) *>
        ZIO.succeed {
          val thread = Thread.currentThread()
          val value = state.notInterruptedCounter.incrementAndGet()
          println(
            s"[LayeredRepro][signalNotInterrupted] attempt=$attempt env=$envId suite=$suiteId test=$testId total=$value thread=${thread.getName}:${thread.getId}"
          )
        }
    }
  }

  private def runEnvViaBlockingRunnerToF(
    runtime: Runtime[Any],
    envEffect: ZIO[Any, Throwable, Unit],
    style: RunnerStyle,
  ): ZIO[Any, Throwable, Unit] = {
    ZIO
      .attemptBlockingInterrupt {
        scala.concurrent.blocking {
          style.runSync(runtime, envEffect)
        }
      }.flatMap {
        case zio.Exit.Success(value) => ZIO.succeed(value)
        case zio.Exit.Failure(cause) => ZIO.failCause(cause)
      }
  }

}
