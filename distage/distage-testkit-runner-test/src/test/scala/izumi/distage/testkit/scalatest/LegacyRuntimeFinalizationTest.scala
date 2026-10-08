package izumi.distage.testkit.scalatest

import izumi.distage.testkit.model.{DistageTest, EnvResult}
import izumi.distage.testkit.runner.api.TestReporter
import izumi.distage.testkit.runner.TestkitRunnerModule
import izumi.distage.testkit.runner.di.{RuntimeExecution, TestRuntime}
import izumi.functional.bio.impl.MiniBIOAsync
import izumi.functional.lifecycle.Lifecycle
import izumi.fundamentals.platform.IzPlatform
import izumi.fundamentals.platform.functional.Identity
import izumi.distage.testkit.runner.spec.Assertion

import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.duration.*
import scala.concurrent.{Future, Promise}
import scala.util.{Failure, Success, Try}

final class LegacyRuntimeFinalizationTest extends LegacyRuntimeFinalizationTestPlatformSpecific {
  import LegacyRuntimeFinalizationTest.*

  private final val Deadline = 10.seconds
  private final val Observation = 250.millis

  "The legacy runtime" should {
    Seq(
      Scenario("complete a successful execution after release", false, false, false, false, false),
      Scenario("retain an allocation release failure", false, false, false, false, true),
      Scenario("retain an execution failure", true, false, false, false, false),
      Scenario("retain independent execution and release failures", true, false, false, false, true),
      Scenario("join execution after a failed stop future", false, true, false, false, false),
      Scenario("join execution after a throwing stop action", false, false, true, false, false),
      Scenario("retain execution, stop and release failures", true, true, false, false, true),
      Scenario("release its allocation after startup failure", false, false, false, true, false),
      Scenario("retain startup and release failures", false, false, false, true, true),
    ).foreach {
      scenario =>
        scenario.name in check(scenario)
    }
  }

  private def check(scenario: Scenario): Future[Assertion] = {
    val released = new AtomicInteger(0)
    val requests = new AtomicInteger(0)
    val callbacks = new AtomicInteger(0)
    val engineCompleted = Promise[Unit]()
    val executionGate = Promise[Unit]()
    val releaseCompleted = Promise[Unit]()
    val executionFailure = new ControlledFailure("execution failure")
    val stopFailure = new ControlledFailure("interruption failure")
    val startupFailure = new ControlledFailure("startup failure")
    val releaseFailure = new ControlledFailure("allocation release failure")
    val controlContext = IzPlatform.platformGlobalExecutionContext
    val lifecycle = Lifecycle
      .makeSimple[Unit](()) {
        _ =>
          try {
            released.incrementAndGet()
            if (scenario.releaseFails) throw releaseFailure
          } finally { val _ = releaseCompleted.trySuccess(()) }
      }.flatMap {
        _ =>
          RuntimeLifecycleFixture.miniBIO().map {
            base =>
              new LegacyRuntimeFinalizationRunnerPlatformSpecific(base) {
                override def runFutureInterruptible[A](effect: => Outer[A]): (Future[A], () => Future[Unit]) = {
                  if (scenario.startupFails) throw startupFailure
                  val (future, stop) = base.runFutureInterruptible(effect)
                  val gated = future.transformWith {
                    result =>
                      engineCompleted.success(())
                      executionGate.future.transform(_ => if (scenario.executionFails) Failure(executionFailure) else result)(using controlContext)
                  }(using controlContext)
                  val controlledStop = () => {
                    requests.incrementAndGet()
                    val _ = stop()
                    if (scenario.stopThrows) throw stopFailure
                    else if (scenario.stopFails) Future.failed(stopFailure)
                    else Future.unit
                  }
                  (gated, controlledStop)
                }
              }
          }
      }
    val runtime = new TestRuntime[Outer](lifecycle, controlContext)
    val control = new RecordingControl
    val expected = List(
      if (scenario.executionFails) Some(executionFailure) else None,
      if (scenario.stopFails || scenario.stopThrows) Some(stopFailure) else None,
      if (scenario.startupFails) Some(startupFailure) else None,
      if (scenario.releaseFails) Some(releaseFailure) else None,
    ).flatten

    def start(): RuntimeExecution[List[EnvResult]] = {
      try runtime.run(TestkitRunnerModule.run[Outer](new EmptyReporter, _ => false, Seq.empty[DistageTest[Identity]], Nil))
      catch { case cause: Throwable => control.completeOuterSuite(Some(cause)); throw cause }
    }

    def checkFailure(cause: Throwable): Assertion = {
      assert(includes(cause, expected))
      if (expected.size == 1) assert(cause eq expected.head)
      assert(expected.forall(error => error.getCause == null && error.getSuppressed.isEmpty))
    }

    if (scenario.startupFails) {
      val attempted = Try(start())
      Future.successful {
        checkFailure(attempted.failed.get)
        control.completed.future.value match {
          case Some(Success(Some(cause))) => checkFailure(cause)
          case other => fail(s"Startup failure was not reported to the suite: $other")
        }
        assert(released.get() == 1)
      }
    } else {
      val callback = Promise[Either[Throwable, List[EnvResult]]]()
      val started = start()
      started.completion.onComplete {
        completed =>
          val result = completed.toEither
          callbacks.incrementAndGet()
          control.completeOuterSuite(result.left.toOption)
          val _ = callback.trySuccess(result)
      }(controlContext)
      val observed = for {
        _ <- withTimeout(engineCompleted.future, Deadline)
        _ = if (scenario.stopFails || scenario.stopThrows) { started.stop(); started.stop() }
        _ <- if (scenario.stopFails || scenario.stopThrows) pause(Observation) else Future.unit
        _ = assert(!control.completed.isCompleted && !callback.isCompleted && released.get() == 0)
        _ = executionGate.success(())
        result <- withTimeout(callback.future, Deadline)
      } yield {
        if (expected.isEmpty) assert(result == Right(Nil))
        else checkFailure(result.left.toOption.get)
        started.stop()
        started.stop()
        assert(requests.get() == (if (scenario.stopFails || scenario.stopThrows) 1 else 0))
        assert(released.get() == 1 && callbacks.get() == 1)
      }
      observed.transformWith {
        result =>
          val _ = executionGate.trySuccess(())
          val cleanup = for {
            _ <- withTimeout(callback.future, Deadline)
            _ <- withTimeout(releaseCompleted.future, Deadline)
          } yield ()
          cleanup.transform {
            case Success(_) => result
            case Failure(cause) =>
              result match {
                case Failure(primary) => primary.addSuppressed(cause); Failure(primary)
                case Success(_) => Failure(cause)
              }
          }
      }
    }
  }
}

private[scalatest] object LegacyRuntimeFinalizationTest {
  type Outer[A] = MiniBIOAsync[Throwable, A]

  final case class Scenario(name: String, executionFails: Boolean, stopFails: Boolean, stopThrows: Boolean, startupFails: Boolean, releaseFails: Boolean)
  final class ControlledFailure(message: String) extends RuntimeException(message, null, false, true)

  def includes(cause: Throwable, expected: List[Throwable]): Boolean = {
    def all(current: Throwable, ancestors: List[Throwable]): List[Throwable] = {
      if (ancestors.exists(_ eq current)) Nil
      else current :: (Option(current.getCause).toList ++ current.getSuppressed.toList).flatMap(next => all(next, current :: ancestors))
    }
    val observed = all(cause, Nil)
    expected.forall(error => observed.exists(_ eq error))
  }

  final class RecordingControl {
    val completed: Promise[Option[Throwable]] = Promise[Option[Throwable]]()
    def completeOuterSuite(mbFailure: Option[Throwable]): Unit = { val _ = completed.trySuccess(mbFailure) }
  }

  final class EmptyReporter extends TestReporter.Noop
}
