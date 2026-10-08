package izumi.distage.testkit.runner.di

import distage.{DefaultModule, DIKey, ModuleDef, TagK}
import izumi.distage.plugins.PluginConfig
import izumi.distage.testkit.model.{SuiteId as EngineSuiteId, TestActivationStrategy, TestConfig}
import izumi.distage.testkit.protocol.*
import izumi.distage.testkit.runner.*
import izumi.distage.testkit.runner.spec.{Spec1, SpecIdentity}
import izumi.functional.bio.impl.MiniBIOAsync
import izumi.functional.lifecycle.Lifecycle
import izumi.functional.quasi.{QuasiAsync, QuasiIO, QuasiIORunner}
import izumi.fundamentals.platform.functional.Identity

import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.{Success, Try}

private[di] object SpecRuntimeFactoryFixtures {
  private final class Resource
  private final class Statistics(fails: Boolean) extends OwnedRuntimeFixture(
    new IllegalStateException("owned runner graph release failure"),
    new IllegalStateException("owned outer runtime release failure"),
    fails,
  ) {
    val ignoredAcquired = new AtomicInteger(0)
    val entered = Promise[Unit]()
    val module = new ModuleDef {
      make[Resource].fromResource(() => Lifecycle.makeSimple[Resource] {
        val _ = acquired.incrementAndGet(); new Resource
      } { _ => val _ = released.incrementAndGet() })
    }
    val config = TestConfig.empty.copy(pluginConfig = PluginConfig.constUnchecked(module),
      memoizationRoots = TestConfig.PriorityAxisDIKeys.fromSet(Set(DIKey[Resource])), activationStrategy = TestActivationStrategy.IgnoreConfig)
  }

  def run(context: ExecutionContext, verify: (String, Boolean) => Unit): Future[Unit] = {
    implicit val ec: ExecutionContext = context
    exercise[MiniBIOAsync[Throwable, _]]("MiniBIO", TestRunnerRuntime.runnerLifecycleForMiniBIOAsync(), context, verify)
      .flatMap(_ => exercise[cats.effect.IO]("Cats IO", TestRunnerRuntime.defaultRunnerLifecycleFor[cats.effect.IO], context, verify))
      .flatMap(_ => exercise[zio.Task]("ZIO", TestRunnerRuntime.defaultRunnerLifecycleFor[zio.Task], context, verify))
      .map { _ => println("SPEC_RUNTIME_FACTORY_CONTRACTS_OK runtimes=3") }
  }

  private def exercise[F[_]: TagK: DefaultModule](label: String, delegate: Lifecycle[Identity, QuasiIORunner[F]], context: ExecutionContext,
    verify: (String, Boolean) => Unit)(implicit F: QuasiIO[F], FA: QuasiAsync[F]): Future[Unit] = {
    implicit val ec: ExecutionContext = context
    Vector("inspection", "execute", "cancel", "cancel-failure", "mixed", "selected", "closed-reuse").foldLeft(Future.unit) { (previous, mode) => previous.flatMap { _ =>
      val fails = mode == "cancel-failure"
      val stats = new Statistics(fails)
      val runtime = stats.runtime(delegate)
      val ignored = TestRunnerRuntime.asyncRuntimeFor[MiniBIOAsync[Throwable, _]](
        Lifecycle.makeSimple[Unit] { val _ = stats.ignoredAcquired.incrementAndGet(); throw new IllegalStateException("Unchosen runtime was acquired") }(_ => ()).flatMap(_ => TestRunnerRuntime.runnerLifecycleForMiniBIOAsync()), Nil)
      val suites: Vector[() => TestSuite] = if (mode.startsWith("cancel")) Vector(() => new CancellingSuite[F](stats, runtime))
        else if (mode == "mixed") Vector(() => new SharingSuite(stats, runtime, "first"), () => new SharingSuite(stats, ignored, "second"))
        else if (mode == "selected") Vector(() => new SharingSuite(stats, ignored, "first"), () => new SharingSuite(stats, runtime, "second"))
        else Vector(() => new SharingSuite(stats, runtime, "first"), () => new SharingSuite(stats, runtime, "second"))
      val identity = CatalogueIdentity(BuildId("runtime-factory"), BuildTargetId(label), CatalogueId(mode))
      val sink = new FixtureSupport.RecordingSink
      def events: Vector[ProtocolMessage.Event] = sink.events
      val session = new RunSession(identity, suites, context, sink)
      val _ = session.discover().fold(failure => throw new IllegalStateException(failure.message), value => value)
      val suspended = stats.outerAcquired.get() == 0 && stats.graphAcquired.get() == 0 && stats.acquired.get() == 0 && stats.bodies.get() == 0
      val selection = if (mode == "selected") Selection.Only(Vector(SuiteId("second")), Vector.empty) else Selection.All
      val request = RunRequest(identity, selection, RunOverrides(Vector.empty, Vector.empty, MemoizationOverride.Inherit))
      val resolved = session.resolve(request).fold(failure => throw new IllegalStateException(failure.message), value => value)
      session.plan(resolved).flatMap { result =>
        val planned = result.fold(failure => throw new IllegalStateException(failure.message), value => value)
        val retained = stats.outerAcquired.get() == 1 && stats.graphAcquired.get() == 1 && stats.outerReleased.get() == 0 && stats.graphReleased.get() == 0 && stats.acquired.get() == 0
        val execution = if (mode == "inspection" || mode == "closed-reuse") session.close().map(_ => Option.empty[RunOutcome])
          else session.execute(RunId(mode), planned).map(Some(_))
        val cancel = if (mode.startsWith("cancel")) stats.entered.future.map { _ => session.cancel(); session.cancel(); () } else Future.unit
        cancel.flatMap(_ => stats.releasing.future).flatMap { _ =>
          val held = !execution.isCompleted && !events.exists(_.event.isInstanceOf[RunEvent.Finished]) && stats.outerReleased.get() == 0 && stats.graphReleased.get() == 0
          val _ = stats.release.trySuccess(())
          execution.transform { outcome =>
            verify(label + " " + mode + " discovery suspends runtime acquisition", suspended)
            verify(label + " " + mode + " planning owns the override graph without application resources", retained)
            verify(label + " " + mode + " completion joins the held graph and outer release", held && stats.graphReleased.get() == 1 && stats.outerReleased.get() == 1)
            outcome.get.foreach { completed =>
              verify(label + " " + mode + " reports every selected identity once", completed.results.size == resolved.tests.size && completed.results.map(_.id).toSet == resolved.tests.map(_.id).toSet && events.count(_.event.isInstanceOf[RunEvent.Finished]) == 1)
              if (mode == "execute" || mode == "mixed" || mode == "selected") verify(label + " " + mode + " selected group shares memoization using one launcher", completed.successful && stats.acquired.get() == 1 && stats.released.get() == 1 && stats.bodies.get() == (if (mode == "selected") 2 else 4) && stats.ignoredAcquired.get() == 0)
              else {
                def contains(failure: Failure, message: String): Boolean = failure.message == message || failure.causes.exists(contains(_, message)) || failure.suppressed.exists(contains(_, message))
                verify(label + " " + mode + " distinguishes cancellation from independent release failures", completed.cancelled && completed.results.forall(_.status == TestStatus.Cancelled) &&
                  (if (fails) completed.failures.exists(contains(_, stats.graphFailure.getMessage)) && completed.failures.exists(contains(_, stats.outerFailure.getMessage)) else completed.failures.isEmpty))
              }
            }
            if (mode == "inspection" || mode == "closed-reuse") verify(label + " inspection releases without a body or application graph", stats.bodies.get() == 0 && stats.acquired.get() == 0 && events.isEmpty)
            if (mode == "closed-reuse") {
              val owner = planned.plans.head.plan
              val attempted = Try(owner.execute(RunExecutionContext(RunId("closed-reuse"), new Cancellation, _ => ())))
              val closed = owner.close()
              println("CLOSED_PLAN_REUSE_OBSERVED runtime=" + label + " failure=" + attempted.failed.toOption + " closeCompleted=" + closed.isCompleted)
              verify(label + " rejected direct execution preserves completed owner closure", closed.isCompleted)
              verify(label + " closed plan rejects direct execution before admission", attempted.failed.toOption.exists(_.getMessage.contains("plan is closed")))
            }
            Success(())
          }
        }.transformWith { result =>
          val _ = stats.release.trySuccess(())
          execution.transformWith(_ => session.close()).transformWith(_ => Future.fromTry(result))
        }
      }
    } }
  }

  private final class SharingSuite(stats: Statistics, runtime: TestRunnerRuntime, name: String) extends SpecIdentity {
    override protected def testRunnerRuntime(): TestRunnerRuntime = runtime
    override protected def distageSuiteId: EngineSuiteId = EngineSuiteId(name)
    override protected def config: TestConfig = stats.config
    "first body" in { (_: Resource) => val _ = stats.bodies.incrementAndGet() }
    "second body" in { (_: Resource) => val _ = stats.bodies.incrementAndGet() }
  }

  private final class CancellingSuite[F[_]: TagK: DefaultModule](stats: Statistics, runtime: TestRunnerRuntime)(implicit F: QuasiIO[F], FA: QuasiAsync[F]) extends Spec1[F] {
    override protected def testRunnerRuntime(): TestRunnerRuntime = runtime
    override protected def config: TestConfig = stats.config
    "held body" in { (_: Resource) => F.flatMap(F.maybeSuspend { val _ = stats.entered.success(()) })(_ => FA.fromFuture(Promise[Unit]().future)) }
  }
}
