package izumi.distage.testkit.runner.di

import izumi.distage.testkit.model.{FullMeta, ScopeId, SuiteMeta, TestStatus}
import izumi.distage.testkit.runner.api.{TestFinalizationReporter, TestReporter}
import izumi.distage.testkit.runner.impl.services.TestConfigLoader
import izumi.functional.bio.impl.MiniBIOAsync
import izumi.functional.lifecycle.Lifecycle
import izumi.functional.quasi.QuasiIORunner

import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.{ExecutionContext, Future, Promise}

private[di] object PreparedRuntimeFixtures {
  private type F[A] = MiniBIOAsync[Throwable, A]

  def run(context: ExecutionContext, verify: (String, Boolean) => Unit): Future[Unit] = {
    implicit val ec: ExecutionContext = context
    val F = MiniBIOAsync.WeakAsyncForMiniBIOAsync
    val interpreter = QuasiIORunner.fromBIO[MiniBIOAsync](using MiniBIOAsync.UnsafeRunMiniBIOAsync(using context))
    val stops = new AtomicInteger(0)
    val acquired = new AtomicInteger(0)
    val released = new AtomicInteger(0)
    val releasing = Promise[Unit]()
    val release = Promise[Unit]()
    val runner = new DistageEngine[F](new TestConfigLoader.TestConfigLoaderImpl, DistageRunnerOptions(false, false)).runner(new EmptyReporter, new TestFinalizationReporter.Rethrowing)
    val observed = new PreparedRuntimeRunnerPlatform[F] {
      override protected val underlying: QuasiIORunner[F] = interpreter
      override def runFuture[A](effect: => F[A]): Future[A] = interpreter.runFuture(effect)
      override def runFutureInterruptible[A](effect: => F[A]): (Future[A], () => Future[Unit]) = {
        val (completion, interrupt) = interpreter.runFutureInterruptible(effect)
        (completion, () => { val _ = stops.incrementAndGet(); interrupt() })
      }
    }
    val resource = Lifecycle.make[F, izumi.distage.testkit.runner.impl.DistageTestRunner[F]](
      F.sync { val _ = acquired.incrementAndGet(); runner }
    ) { _ =>
      F.flatMap(F.sync { val _ = releasing.success(()) })(_ =>
        F.flatMap(F.fromFuture(_ => release.future))(_ => F.sync { val _ = released.incrementAndGet() })
      )
    }
    val runtime = new TestRuntime[F](Lifecycle.pure(observed), context)
    PreparedTestRuntime.acquire(runtime, resource, Nil, context).flatMap { prepared =>
      val retained = acquired.get() == 1 && released.get() == 0
      val first = prepared.close()
      releasing.future.flatMap { _ =>
        val second = prepared.close()
        val repeated = stops.get()
        val held = !first.isCompleted && !second.isCompleted && released.get() == 0
        val _ = release.success(())
        Future.sequence(Vector(first, second)).map { _ =>
          verify("prepared plan retains its acquired runner", retained)
          verify("inspection close waits for held runner finalization", held && released.get() == 1)
          verify("repeated inspection close joins without interrupting finalization", repeated == 0)
          verify("repeated inspection close returns the same completion", first eq second)
        }
      }
    }
  }

  private[di] final class EmptyReporter extends TestReporter {
    override def beginScope(id: ScopeId): Unit = ()
    override def endScope(id: ScopeId): Unit = ()
    override def beginLevel(scope: ScopeId, depth: Int, suites: List[SuiteMeta]): Unit = ()
    override def endLevel(scope: ScopeId, depth: Int, suites: List[SuiteMeta]): Unit = ()
    override def beginSuite(scopeId: ScopeId, depth: Int, suiteMeta: SuiteMeta): Unit = ()
    override def endSuite(scopeId: ScopeId, depth: Int, suiteMeta: SuiteMeta): Unit = ()
    override def testSetupStatus(scopeId: ScopeId, depth: Int, meta: FullMeta, testStatus: TestStatus.Setup): Unit = ()
    override def testStatus(scope: ScopeId, depth: Int, meta: FullMeta, testStatus: TestStatus): Unit = ()
  }
}
