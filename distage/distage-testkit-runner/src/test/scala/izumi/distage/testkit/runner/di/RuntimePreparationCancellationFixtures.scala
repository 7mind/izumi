package izumi.distage.testkit.runner.di

import distage.{DefaultModule, ModuleDef, TagK}
import izumi.distage.plugins.PluginConfig
import izumi.distage.testkit.model.{DistageTest, TestActivationStrategy, TestConfig}
import izumi.distage.testkit.protocol.*
import izumi.distage.testkit.runner.*
import izumi.distage.testkit.runner.api.TestReporter
import izumi.distage.testkit.runner.impl.{DistageTestRunner, RunnerToF, TestPlanner}
import izumi.distage.testkit.runner.impl.services.{ParTraverseExt, TestkitLogging, TestResourceLifecycle, TestStatusConverter, TimedActionF}
import izumi.distage.testkit.runner.spec.SpecIdentity
import izumi.functional.bio.impl.MiniBIOAsync
import izumi.functional.lifecycle.Lifecycle
import izumi.functional.quasi.{QuasiAsync, QuasiIO, QuasiIORunner}
import izumi.fundamentals.platform.functional.Identity
import izumi.fundamentals.platform.language.types.HigherKindedAny.AnyF

import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.concurrent.duration.*

private[di] object RuntimePreparationCancellationFixtures {
  private final class State(val invalid: Boolean) extends ResourceStatistics {
    val stop = Promise[() => Future[Unit]]()
    val entered = Promise[Unit]()
    val interrupted = Promise[Unit]()
    val planGate = Promise[Unit]()
    val releasing = Promise[Unit]()
    val release = Promise[Unit]()
    val outerReleased = new AtomicInteger(0)
  }

  private sealed trait Shutdown
  private case object SessionClose extends Shutdown
  private case object ApplicationClose extends Shutdown
  private case object ApplicationCancel extends Shutdown
  private case object LauncherCancel extends Shutdown
  private final case class Control(planning: Future[Unit], requestStop: () => Future[Unit], close: () => Future[Unit], rejected: () => Boolean)

  def run(context: ExecutionContext, verify: (String, Boolean) => Unit): Future[Unit] = {
    implicit val ec: ExecutionContext = context
    Vector(SessionClose, ApplicationClose, ApplicationCancel, LauncherCancel).foldLeft(Future.unit) { (previous, shutdown) => previous.flatMap { _ =>
      exercise[MiniBIOAsync[Throwable, _]]("MiniBIO", TestRunnerRuntime.runnerLifecycleForMiniBIOAsync(), shutdown, context, verify)
        .flatMap(_ => exercise[cats.effect.IO]("Cats IO", TestRunnerRuntime.defaultRunnerLifecycleFor[cats.effect.IO], shutdown, context, verify))
        .flatMap(_ => exercise[zio.Task]("ZIO", TestRunnerRuntime.defaultRunnerLifecycleFor[zio.Task], shutdown, context, verify))
    } }.map(_ => println("RUNTIME_PREPARATION_CANCELLATION_OK runtimes=3 shutdowns=4"))
  }

  private def exercise[F[_]: TagK: DefaultModule](label: String, delegate: Lifecycle[Identity, QuasiIORunner[F]], shutdown: Shutdown,
    context: ExecutionContext, verify: (String, Boolean) => Unit)(implicit F: QuasiIO[F], FA: QuasiAsync[F]): Future[Unit] = {
    implicit val ec: ExecutionContext = context
    val state = new State(false)
    val lifecycle = Lifecycle.makeSimple[Unit](())(_ => { val _ = state.outerReleased.incrementAndGet() }).flatMap(_ => delegate)
    val module = new ModuleDef {
      make[State].fromValue(state)
      make[DistageTestRunner[F]].from[HeldRunner[F]]
      make[TestkitLogging].fromResource(() => Lifecycle.make[F, TestkitLogging](F.maybeSuspend {
        val _ = state.acquired.incrementAndGet()
        new TestkitLogging { override def enableDebugOutput: Boolean = false }
      })(_ => F.flatMap(F.maybeSuspend { val _ = state.releasing.success(()) })(_ =>
        F.flatMap(FA.fromFuture(state.release.future))(_ => F.maybeSuspend { val _ = state.released.incrementAndGet() })
      )))
    }
    val runtime = TestRunnerRuntime.asyncRuntimeFor[F](lifecycle, List(module))
    val identity = CatalogueIdentity(BuildId("preparation-cancel"), BuildTargetId(label), CatalogueId(shutdown.toString))
    val suite = new SpecIdentity {
      override protected def testRunnerRuntime(): TestRunnerRuntime = runtime
      override protected def config: TestConfig = TestConfig.empty.copy(pluginConfig = PluginConfig.constUnchecked(new ModuleDef {}), activationStrategy = TestActivationStrategy.IgnoreConfig)
      "never run during preparation" in { val _ = state.bodies.incrementAndGet() }
    }
    val request = RunRequest(identity, Selection.All, RunOverrides(Vector.empty, Vector.empty, MemoizationOverride.Inherit))
    var messages = Vector.empty[ProtocolMessage]
    val output = new ProtocolOutput { override def accept(message: ProtocolMessage): Unit = synchronized { messages :+= message } }
    def rejected(): Boolean = messages.exists(_.isInstanceOf[ProtocolMessage.Rejected]) && !messages.exists(_.isInstanceOf[ProtocolMessage.Planned])
    val control = shutdown match {
      case SessionClose =>
        val session = new RunSession(identity, Vector(() => suite), context, new EventSink { override def accept(event: ProtocolMessage.Event): Unit = output.accept(event) })
        val resolved = session.resolve(request).fold(failure => throw new IllegalStateException(failure.message), value => value)
        var refused = false
        val planning = session.plan(resolved).map { result => refused = result.isLeft }
        Control(planning, () => session.close(), () => session.close(), () => refused && messages.isEmpty)
      case ApplicationClose | ApplicationCancel =>
        val application = new TestApplication(RunId("application-close"), identity, Vector(() => suite), context, output)
        val planning = application.accept(ProtocolMessage.Request(RequestOperation.Plan, application.run, request))
        val stop = if (shutdown == ApplicationClose) () => application.close() else () => application.accept(ProtocolMessage.Cancel(application.run))
        Control(planning, stop, () => application.close(), () => rejected())
      case LauncherCancel =>
        val command = ProtocolMessage.Request(RequestOperation.Plan, RunId("launcher-cancel"), request)
        val source = new ProtocolFrameSource {
          private var pending = true
          override def readFrame(): Option[String] = if (pending) { pending = false; Some(ProtocolCodec.encode(command)) } else None
          override def close(): Unit = ()
        }
        val launched = ApplicationLauncher.start(identity, Vector(() => suite), context, source, output)
        Control(launched.completion.map(_ => ()), () => { launched.cancel(); Future.unit }, () => launched.completion.map(_ => ()), () => rejected())
    }
    val clock = QuasiIORunner.fromBIO[MiniBIOAsync](using MiniBIOAsync.UnsafeRunMiniBIOAsync(using context))
    val checked = state.entered.future.flatMap { _ =>
      val stopping = control.requestStop()
      val observed = Future.firstCompletedOf(Vector(state.interrupted.future.map(_ => true), clock.runFuture(MiniBIOAsync.WeakAsyncForMiniBIOAsync.sleep(300.millis)).map(_ => false)))
      observed.flatMap { interrupted =>
        val closing = control.close()
        val _ = state.planGate.trySuccess(())
        state.releasing.future.flatMap { _ =>
          val held = !closing.isCompleted && !control.planning.isCompleted && state.released.get() == 0 && state.outerReleased.get() == 0
          val immediate = shutdown != ApplicationCancel && shutdown != LauncherCancel || stopping.isCompleted
          val _ = state.release.trySuccess(())
          closing.flatMap(_ => control.planning).map { _ =>
            verify(label + " " + shutdown + " interrupts planning before prepared-handle publication", interrupted && immediate)
            verify(label + " " + shutdown + " joins the held runner graph and outer release", held && state.acquired.get() == 1 && state.released.get() == 1 && state.outerReleased.get() == 1)
            verify(label + " " + shutdown + " never publishes an executable plan or runs a body", control.rejected() && state.bodies.get() == 0)
          }
        }
      }
    }
    checked.transformWith { result =>
      val _ = (state.planGate.trySuccess(()), state.release.trySuccess(()))
      control.close().transformWith(_ => control.planning).transformWith(_ => Future.fromTry(result))
    }
  }

  def maskedAcquisition(context: ExecutionContext, verify: (String, Boolean) => Unit): Future[Unit] = {
    implicit val ec: ExecutionContext = context
    masked[MiniBIOAsync[Throwable, _]]("MiniBIO", TestRunnerRuntime.runnerLifecycleForMiniBIOAsync(), context, verify)
      .flatMap(_ => masked[cats.effect.IO]("Cats IO", TestRunnerRuntime.defaultRunnerLifecycleFor[cats.effect.IO], context, verify))
      .flatMap(_ => masked[zio.Task]("ZIO", TestRunnerRuntime.defaultRunnerLifecycleFor[zio.Task], context, verify))
      .map(_ => println("RUNTIME_MASKED_ACQUISITION_CLOSE_OK runtimes=3"))
  }

  private def masked[F[_]: TagK: DefaultModule](label: String, delegate: Lifecycle[Identity, QuasiIORunner[F]], context: ExecutionContext,
    verify: (String, Boolean) => Unit)(implicit F: QuasiIO[F], FA: QuasiAsync[F]): Future[Unit] = {
    implicit val ec: ExecutionContext = context
    val state = new State(false)
    val lifecycle = Lifecycle.makeSimple[Unit](())(_ => { val _ = state.outerReleased.incrementAndGet() }).flatMap(_ => delegate)
    val module = new ModuleDef {
      make[TestkitLogging].fromResource(() => Lifecycle.make[F, TestkitLogging](
        F.flatMap(F.maybeSuspend { val _ = state.entered.success(()) })(_ => F.flatMap(FA.fromFuture(state.planGate.future))(_ => F.maybeSuspend {
          val _ = state.acquired.incrementAndGet()
          new TestkitLogging { override def enableDebugOutput: Boolean = false }
        }))
      )(_ => F.flatMap(F.maybeSuspend { val _ = state.releasing.success(()) })(_ =>
        F.flatMap(FA.fromFuture(state.release.future))(_ => F.maybeSuspend { val _ = state.released.incrementAndGet() })
      )))
    }
    val runtime = TestRunnerRuntime.asyncRuntimeFor[F](lifecycle, List(module))
    val suite = new SpecIdentity {
      override protected def testRunnerRuntime(): TestRunnerRuntime = runtime
      override protected def config: TestConfig = TestConfig.empty.copy(pluginConfig = PluginConfig.constUnchecked(new ModuleDef {}), activationStrategy = TestActivationStrategy.IgnoreConfig)
      "masked allocation never executes" in { val _ = state.bodies.incrementAndGet() }
    }
    val identity = CatalogueIdentity(BuildId("masked-acquisition"), BuildTargetId(label), CatalogueId("close"))
    val session = new RunSession(identity, Vector(() => suite), context, FixtureSupport.silentSink())
    val resolved = session.resolve(RunRequest(identity, Selection.All, RunOverrides(Vector.empty, Vector.empty, MemoizationOverride.Inherit))).fold(failure => throw new IllegalStateException(failure.message), value => value)
    val planning = session.plan(resolved)
    val checked = state.entered.future.flatMap { _ =>
      val closing = session.close()
      val beforeAcquired = !closing.isCompleted && !planning.isCompleted && state.acquired.get() == 0 && state.outerReleased.get() == 0
      val _ = state.planGate.trySuccess(())
      state.releasing.future.flatMap { _ =>
        val held = !closing.isCompleted && !planning.isCompleted && state.acquired.get() == 1 && state.released.get() == 0 && state.outerReleased.get() == 0
        val _ = state.release.trySuccess(())
        closing.flatMap(_ => planning).map { result =>
          verify(label + " close during masked allocation joins acquisition", beforeAcquired)
          verify(label + " masked allocation close joins held graph and outer release", held && state.released.get() == 1 && state.outerReleased.get() == 1)
          verify(label + " masked allocation close rejects execution after publication", result.isLeft && state.bodies.get() == 0)
        }
      }
    }
    checked.transformWith { result =>
      val _ = (state.planGate.trySuccess(()), state.release.trySuccess(()))
      session.close().transformWith(_ => planning).transformWith(_ => Future.fromTry(result))
    }
  }

  def invalidInspection(context: ExecutionContext, verify: (String, Boolean) => Unit): Future[Unit] = {
    implicit val ec: ExecutionContext = context
    val state = new State(true)
    type F[A] = MiniBIOAsync[Throwable, A]
    val F = MiniBIOAsync.WeakAsyncForMiniBIOAsync
    val delegate = QuasiIORunner.fromBIO[MiniBIOAsync](using MiniBIOAsync.UnsafeRunMiniBIOAsync(using context))
    val observed = new PreparedRuntimeRunnerPlatform[F] {
      override protected val underlying: QuasiIORunner[F] = delegate
      override def runFuture[A](effect: => F[A]): Future[A] = delegate.runFuture(effect)
      override def runFutureInterruptible[A](effect: => F[A]): (Future[A], () => Future[Unit]) = {
        val result = delegate.runFutureInterruptible(effect)
        val _ = state.stop.success(result._2)
        result
      }
    }
    val lifecycle = Lifecycle.makeSimple[Unit](())(_ => { val _ = state.outerReleased.incrementAndGet() }).map(_ => observed: QuasiIORunner[F])
    val module = new ModuleDef {
      make[State].fromValue(state)
      make[DistageTestRunner[F]].from[HeldRunner[F]]
      make[TestkitLogging].fromResource(() => Lifecycle.make[F, TestkitLogging](F.sync {
        val _ = state.acquired.incrementAndGet()
        new TestkitLogging { override def enableDebugOutput: Boolean = false }
      })(_ => F.flatMap(F.sync { val _ = state.releasing.success(()) })(_ =>
        F.flatMap(F.fromFuture(_ => state.release.future))(_ => F.sync { val _ = state.released.incrementAndGet() })
      )))
    }
    val factory = TestRunnerRuntime.asyncRuntimeFor[F](lifecycle, List(module))
    val suite = new SpecIdentity {
      override protected def testRunnerRuntime(): TestRunnerRuntime = factory
      override protected def config: TestConfig = TestConfig.empty.copy(pluginConfig = PluginConfig.constUnchecked(new ModuleDef {}), activationStrategy = TestActivationStrategy.IgnoreConfig)
      "invalid inspection never executes" in { val _ = state.bodies.incrementAndGet() }
    }
    val identity = CatalogueIdentity(BuildId("inspection-projection"), BuildTargetId("invalid-uid"), CatalogueId("owned"))
    val session = new RunSession(identity, Vector(() => suite), context, FixtureSupport.silentSink())
    val resolved = session.resolve(RunRequest(identity, Selection.All, RunOverrides(Vector.empty, Vector.empty, MemoizationOverride.Inherit))).fold(failure => throw new IllegalStateException(failure.message), value => value)
    val planning = session.plan(resolved)
    state.entered.future.flatMap { _ =>
      val observedRelease = Future.firstCompletedOf(Vector(state.releasing.future.map(_ => true), delegate.runFuture(F.sleep(300.millis)).map(_ => false)))
      observedRelease.flatMap { releasing =>
        val held = !planning.isCompleted && state.released.get() == 0 && state.outerReleased.get() == 0
        val stopped = if (releasing) Future.unit else state.stop.future.flatMap(_())
        val _ = state.release.trySuccess(())
        stopped.flatMap(_ => planning).flatMap { result => session.close().map { _ =>
          verify("inspection projection failure closes its unpublished runner owner", releasing)
          verify("inspection projection failure waits for held graph and outer cleanup", held && state.acquired.get() == 1 && state.released.get() == 1 && state.outerReleased.get() == 1)
          verify("inspection projection retains the unknown-identity failure and executes no body", result.left.toOption.exists(_.message.contains("unknown transient test identity")) && state.bodies.get() == 0)
        } }
      }
    }
  }

  private final class HeldRunner[F[_]: TagK](state: State, reporter: TestReporter, logging: TestkitLogging, planner: TestPlanner,
    status: TestStatusConverter, timed: TimedActionF[F], resources: TestResourceLifecycle[F], conversion: RunnerToF[F], parallel: ParTraverseExt[F])
    (implicit F: QuasiIO[F], FA: QuasiAsync[F]) extends DistageTestRunner[F](reporter, logging, planner, status, timed, resources, conversion, parallel) {
    override def plan(tests: Seq[DistageTest[AnyF]]): F[DistageTestRunner.PreparedRun[F]] = F.guaranteeOnInterrupt(
      F.flatMap(F.maybeSuspend { val _ = state.entered.success(()) })(_ => F.flatMap(if (state.invalid) F.unit else FA.fromFuture(state.planGate.future))(_ => super.plan(if (state.invalid) tests.map(test => test.copy(testMeta = test.testMeta.copy(uid = -1L))) else tests)))
    )(_ => F.maybeSuspend { val _ = state.interrupted.trySuccess(()) })
  }
}
