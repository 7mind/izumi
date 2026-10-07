package izumi.distage.testkit.scalatest

import distage.{Identity, ModuleDef}
import izumi.distage.testkit.model.{DistageTest, FullMeta, ScopeId, SuiteMeta, TestStatus}
import izumi.distage.testkit.runner.api.TestReporter
import izumi.distage.testkit.runner.di.TestRunnerRuntime
import izumi.distage.testkit.runner.spec.AnyWordSpec
import izumi.distage.testkit.scalatest.LegacyRuntimeReleaseTest.TrackedContext
import izumi.functional.bio.impl.MiniBIOAsync
import izumi.functional.lifecycle.Lifecycle
import izumi.functional.quasi.QuasiIORunner
import izumi.fundamentals.platform.IzPlatform
import izumi.fundamentals.platform.language.Quirks.Discarder

import java.util.concurrent.{CountDownLatch, TimeUnit}
import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.{Await, Future, Promise}
import scala.concurrent.duration.*
import scala.util.Try

final class RunnerOverrideLifecycleTest extends AnyWordSpec {
  "Resource-bearing runner overrides" should {
    "finalize the runner graph before releasing its owned outer runtime" in {
      assert(RunnerOverrideLifecycleTest.check(failures = false))
    }
    "retain runner graph and outer runtime release failures" in {
      assert(RunnerOverrideLifecycleTest.check(failures = true))
    }
  }
}

object RunnerOverrideLifecycleTest {
  private type Outer[A] = MiniBIOAsync[Throwable, A]
  private final val Deadline = 10.seconds
  private final val Observation = 250.millis

  private def check(failures: Boolean): Boolean = {
    val context = new TrackedContext
    val acquired = new AtomicInteger(0)
    val released = new AtomicInteger(0)
    val begun = new AtomicInteger(0)
    val ended = new AtomicInteger(0)
    val outerReleased = new AtomicInteger(0)
    val callbacks = new AtomicInteger(0)
    val graphReleaseEntered = Promise[Unit]()
    val callback = Promise[Unit]()
    val gate = new CountDownLatch(1)
    val graphFailure = new IllegalStateException("Controlled runner override release failure")
    val outerFailure = new IllegalStateException("Controlled owned outer runtime release failure")
    val reporter = new TestReporter {
      override def beginScope(id: ScopeId): Unit = begun.incrementAndGet().discard()
      override def endScope(id: ScopeId): Unit = ended.incrementAndGet().discard()
      override def beginLevel(scope: ScopeId, depth: Int, suites: List[SuiteMeta]): Unit = ()
      override def endLevel(scope: ScopeId, depth: Int, suites: List[SuiteMeta]): Unit = ()
      override def beginSuite(scopeId: ScopeId, depth: Int, suiteMeta: SuiteMeta): Unit = ()
      override def endSuite(scopeId: ScopeId, depth: Int, suiteMeta: SuiteMeta): Unit = ()
      override def testSetupStatus(scopeId: ScopeId, depth: Int, meta: FullMeta, status: TestStatus.Setup): Unit = ()
      override def testStatus(scope: ScopeId, depth: Int, meta: FullMeta, status: TestStatus): Unit = ()
    }
    val module = new ModuleDef {
      make[TestReporter].fromResource(Lifecycle.makeSimple[TestReporter] {
        acquired.incrementAndGet().discard()
        reporter
      } { _ =>
        graphReleaseEntered.success(()).discard()
        require(gate.await(Deadline.toMillis, TimeUnit.MILLISECONDS), "Runner override release gate timed out")
        released.incrementAndGet().discard()
        if (failures) throw graphFailure
      })
    }
    val lifecycle = Lifecycle.makeSimple[QuasiIORunner[Outer]] {
      QuasiIORunner.fromBIO[MiniBIOAsync](using MiniBIOAsync.UnsafeRunMiniBIOAsync(using context))
    } { _ =>
      context.close()
      require(released.get() == 1, "Owned outer runtime released before its runner graph")
      outerReleased.incrementAndGet().discard()
      if (failures) throw outerFailure
    }
    val runtime = TestRunnerRuntime.asyncRuntimeFor[Outer](lifecycle, List(module))
    val completionContext = IzPlatform.platformGlobalExecutionContext
    val completion = Future {
      runtime.runTests(new LegacyRuntimeFinalizationTest.EmptyReporter, _ => false, Seq.empty[DistageTest[Identity]])
    }(completionContext).flatMap(_.completion)(completionContext)
    completion.onComplete { _ =>
      callbacks.incrementAndGet().discard()
      callback.success(()).discard()
    }(IzPlatform.platformGlobalExecutionContext)
    val observed = Try {
      Await.result(graphReleaseEntered.future, Deadline)
      Thread.sleep(Observation.toMillis)
      acquired.get() == 1 && released.get() == 0 && outerReleased.get() == 0 && callbacks.get() == 0 &&
      !completion.isCompleted && begun.get() == 1 && ended.get() == 1
    }
    gate.countDown()
    val result = Try(Await.result(completion, Deadline))
    Await.result(callback.future, Deadline)
    context.close()
    def includes(cause: Throwable, expected: Throwable, ancestors: List[Throwable]): Boolean =
      if (ancestors.exists(_ eq cause)) false
      else (cause eq expected) || (Option(cause.getCause).toList ++ cause.getSuppressed.toList).exists(next => includes(next, expected, cause :: ancestors))
    val retained = if (failures) result.failed.toOption.exists(cause => includes(cause, graphFailure, Nil) && includes(cause, outerFailure, Nil)) else result.isSuccess
    println("RUNNER_OVERRIDE_LIFECYCLE failures=" + failures + " graphAcquired=" + acquired.get() + " graphReleased=" + released.get() + " outerReleased=" + outerReleased.get() + " callbacks=" + callbacks.get() + " retained=" + retained)
    observed.get && retained && released.get() == 1 && outerReleased.get() == 1 && callbacks.get() == 1
  }
}
