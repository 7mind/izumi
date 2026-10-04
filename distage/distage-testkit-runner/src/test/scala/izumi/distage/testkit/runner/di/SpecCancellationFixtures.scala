package izumi.distage.testkit.runner.di

import cats.effect.IO
import distage.{DefaultModule2, DIKey, TagK}
import izumi.distage.modules.DefaultModule
import izumi.distage.plugins.{PluginConfig, PluginDef}
import izumi.distage.testkit.model.{TestActivationStrategy, TestConfig}
import izumi.distage.testkit.protocol.*
import izumi.distage.testkit.runner.*
import izumi.distage.testkit.runner.spec.{Spec1, Spec2}
import izumi.functional.bio.impl.MiniBIOAsync
import izumi.functional.lifecycle.Lifecycle
import izumi.functional.quasi.{QuasiAsync, QuasiIO, QuasiIORunner}
import izumi.logstage.api.Log
import zio.ZIO

import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.concurrent.duration.*
import scala.util.control.NonFatal

private[di] object SpecCancellationFixtures {
  private final val CancellationTimeout = 10.seconds
  private final val CompletionObservation = 250.millis
  private final class Resource
  private final class Statistics(val failRelease: Boolean) {
    val acquired = new AtomicInteger(0)
    val released = new AtomicInteger(0)
    val bodies = new AtomicInteger(0)
    val entered = Promise[Unit]()
    val interrupted = Promise[Unit]()
    val bodyGate = Promise[Unit]()
    val releaseEntered = Promise[Unit]()
    val releaseGate = Promise[Unit]()
    val releaseCompleted = Promise[Unit]()
    val releaseFailure = new IllegalStateException("active cancellation finalizer failure")
  }

  def run(context: ExecutionContext, verify: (String, Boolean) => Unit): Future[Unit] = {
    implicit val ec: ExecutionContext = context
    val zioDefaults = implicitly[DefaultModule[zio.Task]]
    val runtime = QuasiIORunner.fromBIO[MiniBIOAsync](using MiniBIOAsync.UnsafeRunMiniBIOAsync(using context))
    val cases = Vector("cats", "zio").flatMap(kind => Vector(false, true).map(kind -> _))
    cases.foldLeft(Future.successful(())) { case (before, (kind, failRelease)) => before.flatMap { _ =>
      val stats = new Statistics(failRelease)
      val label = kind + " active cancellation failRelease=" + failRelease + " "
      val identity = CatalogueIdentity(BuildId("spec-cancellation"), BuildTargetId("spec-target"), CatalogueId(kind + "-" + failRelease))
      val factory: () => TestSuite = kind match {
        case "cats" => () => new CatsSuite(stats, TestConfig.Parallelism.Sequential)
        case "zio" => () => new ZIOSuite(stats, TestConfig.Parallelism.Sequential)(using zioDefaults)
      }
      val sink = new RecordingSink(stats)
      val session = new RunSession(identity, Vector(factory), context, sink)
      val catalogue = session.discover().fold(failure => throw new IllegalStateException(failure.message), value => value)
      verify(label + "discovery suspends every resource and body", catalogue.tests.size == 3 && stats.acquired.get() == 0 && stats.bodies.get() == 0 && sink.events.isEmpty)
      val request = RunRequest(identity, Selection.All, RunOverrides(Vector.empty, Vector.empty, MemoizationOverride.Inherit))
      val execution = session.execute(RunId(kind + "-" + failRelease), request)
      val checked = stats.entered.future.flatMap { _ =>
        verify(label + "starts the active body with one shared resource", stats.acquired.get() == 1 && stats.released.get() == 0 && stats.bodies.get() == 2)
        session.cancel()
        session.cancel()
        timed(stats.interrupted.future.map(_ => true), CancellationTimeout, () => false, runtime, context).flatMap { interrupted =>
          if (!interrupted) {
            val _ = (stats.bodyGate.trySuccess(()), stats.releaseGate.trySuccess(()))
            execution.flatMap(_ => Future.failed(new AssertionError(label + "did not interrupt the active body within " + CancellationTimeout)))
          } else stats.releaseEntered.future.flatMap { _ =>
            verify(label + "waits for the held finalizer", !execution.isCompleted && stats.released.get() == 0 && !sink.events.exists(_.event.isInstanceOf[RunEvent.Finished]))
            verify(label + "interrupts without releasing the body gate", !stats.bodyGate.isCompleted && stats.bodies.get() == 2)
            session.cancel()
            require(stats.releaseGate.trySuccess(()), "Cancellation finalizer gate opened twice")
            execution.map { outcome =>
              println("DISTAGE_SPEC_CANCELLATION_OUTCOME kind=" + kind + " failRelease=" + failRelease + " outcome=" + outcome)
              verify(label + "releases its memoized resource exactly once", stats.acquired.get() == 1 && stats.released.get() == 1)
              verify(label + "retains every selected terminal identity", outcome.cancelled && !outcome.successful && outcome.results.map(_.id).toSet == catalogue.tests.map(_.id).toSet)
              val statuses = outcome.results.map(result => result.id.path -> result.status).toMap
              verify(label + "preserves the prior success and cancels remaining tests", statuses == Map(Vector("before cancellation") -> TestStatus.Succeeded, Vector("active cancellation") -> TestStatus.Cancelled, Vector("after cancellation") -> TestStatus.Cancelled))
              def containsReleaseFailure(failure: Failure): Boolean = failure.message.contains(stats.releaseFailure.getMessage) || failure.causes.exists(containsReleaseFailure)
              verify(label + "preserves actual finalizer failures", if (failRelease) outcome.failures.exists(failure => failure.phase == FailurePhase.Finalization && containsReleaseFailure(failure)) else outcome.failures.isEmpty)
              val completed = sink.events.collect { case ProtocolMessage.Event(_, RunEvent.TestCompleted(_, result)) => result.id }
              verify(label + "reports each completion exactly once", completed.size == 3 && completed.distinct.size == 3 && completed.toSet == catalogue.tests.map(_.id).toSet)
              verify(label + "finishes once after cleanup with contiguous event ordinals", sink.events.last.event == RunEvent.Finished(outcome.run, outcome) && sink.events.count(_.event.isInstanceOf[RunEvent.Finished]) == 1 && sink.events.map(_.sequence) == sink.events.indices.map(_.toLong).toVector)
              println("DISTAGE_SPEC_CANCELLATION kind=" + kind + " failRelease=" + failRelease + " results=" + outcome.results.size + " failures=" + outcome.failures.size + " acquired=" + stats.acquired.get() + " released=" + stats.released.get())
            }
          }
        }
      }
      checked.recoverWith { case NonFatal(cause) =>
        val _ = (stats.bodyGate.trySuccess(()), stats.releaseGate.trySuccess(()))
        execution.transformWith(_ => Future.failed(cause))
      }
    } }
  }

  def parallel(context: ExecutionContext, verify: (String, Boolean) => Unit): Future[Unit] = {
    implicit val ec: ExecutionContext = context
    val cats = new Statistics(false)
    val zio = new Statistics(false)
    val stats = Vector(cats, zio)
    val identity = CatalogueIdentity(BuildId("parallel-cancellation"), BuildTargetId("parallel-target"), CatalogueId("cats-zio"))
    val sink = new ParallelRecordingSink
    val factories: Vector[() => TestSuite] = Vector[() => TestSuite](
      () => new CatsSuite(cats, TestConfig.Parallelism.Fixed(2)),
      () => new ZIOSuite(zio, TestConfig.Parallelism.Fixed(2)),
    )
    val session = new RunSession(identity, factories, context, sink)
    val catalogue = session.discover().fold(failure => throw new IllegalStateException(failure.message), value => value)
    val request = RunRequest(identity, Selection.All, RunOverrides(Vector.empty, Vector.empty, MemoizationOverride.Inherit))
    val execution = session.execute(RunId("parallel-cancellation"), request)
    val runtime = QuasiIORunner.fromBIO[MiniBIOAsync](using MiniBIOAsync.UnsafeRunMiniBIOAsync(using context))
    def bounded[A](phase: String, signal: Future[A]): Future[A] = {
      timed(signal, CancellationTimeout, () => throw new AssertionError("Parallel cancellation timed out during " + phase), runtime, context)
    }
    val checked = bounded("body entry", Future.sequence(stats.map(_.entered.future))).flatMap { _ =>
      verify("parallel cancellation starts both effect environments", stats.forall(s => s.acquired.get() == 1 && s.bodies.get() == 2 && s.released.get() == 0))
      session.cancel()
      bounded("finalizer entry", Future.sequence(stats.map(_.releaseEntered.future))).flatMap { _ =>
        timed(execution.map(_ => true), CompletionObservation, () => false, runtime, context).flatMap { completed =>
          println("DISTAGE_PARALLEL_CANCELLATION_HELD completed=" + execution.isCompleted + " released=" + stats.map(_.released.get()) + " finished=" + sink.events.exists(_.event.isInstanceOf[RunEvent.Finished]))
          verify("parallel cancellation joins both held environment finalizers", !completed && !execution.isCompleted && stats.forall(_.released.get() == 0) && !sink.events.exists(_.event.isInstanceOf[RunEvent.Finished]))
          session.cancel()
          stats.foreach { s => val _ = s.bodyGate.trySuccess(()) }
          require(cats.releaseGate.trySuccess(()), "Cats parallel finalizer gate opened twice")
          bounded("first environment release", cats.releaseCompleted.future).flatMap { _ =>
            verify("parallel cancellation waits for the remaining environment", !execution.isCompleted && zio.released.get() == 0)
            require(zio.releaseGate.trySuccess(()), "ZIO parallel finalizer gate opened twice")
            bounded("terminal completion", execution).map { outcome =>
              verify("parallel cancellation releases both resources once", stats.forall(s => s.acquired.get() == 1 && s.released.get() == 1))
              verify("parallel cancellation retains all selected results", outcome.cancelled && outcome.results.size == 6 && outcome.results.map(_.id).toSet == catalogue.tests.map(_.id).toSet)
              verify("parallel cancellation finishes once after both releases", sink.events.count(_.event.isInstanceOf[RunEvent.Finished]) == 1 && sink.events.last.event == RunEvent.Finished(outcome.run, outcome))
              println("DISTAGE_PARALLEL_CANCELLATION_OK results=" + outcome.results.size + " acquired=" + stats.map(_.acquired.get()) + " released=" + stats.map(_.released.get()))
            }
          }
        }
      }
    }
    checked.transformWith { result =>
      stats.foreach { s => val _ = (s.bodyGate.trySuccess(()), s.releaseGate.trySuccess(())) }
      bounded("cleanup", Future.sequence(stats.map(_.releaseCompleted.future)).flatMap(_ => execution)).transformWith(_ => Future.fromTry(result))
    }
  }

  def applicationChannelLoss(context: ExecutionContext, verify: (String, Boolean) => Unit): Future[Unit] = {
    implicit val ec: ExecutionContext = context
    val zioDefaults = implicitly[DefaultModule[zio.Task]]
    val runtime = QuasiIORunner.fromBIO[MiniBIOAsync](using MiniBIOAsync.UnsafeRunMiniBIOAsync(using context))
    Vector("cats", "zio").foldLeft(Future.unit) { (previous, kind) => previous.flatMap { _ =>
      val stats = new Statistics(false)
      val writesAfterLoss = new AtomicInteger(0)
      val failedWrite = new IllegalStateException(kind + " active DI application channel failure")
      val label = kind + " application channel loss "
      val run = RunId("di-channel-" + kind)
      val identity = CatalogueIdentity(BuildId("di-channel-build"), BuildTargetId("di-channel-target"), CatalogueId(kind))
      var messages = Vector.empty[ProtocolMessage]
      val output = new ProtocolOutput {
        override def accept(message: ProtocolMessage): Unit = synchronized {
          require(writesAfterLoss.get() == 0, "Failed DI channel received another write")
          message match {
            case ProtocolMessage.Event(_, RunEvent.TestCompleted(_, result)) if result.id.path == Vector("trigger channel loss") =>
              writesAfterLoss.incrementAndGet()
              throw failedWrite
            case _ =>
              require(ProtocolCodec.decode(ProtocolCodec.encode(message)) == Right(message), "DI application frame must round-trip")
              messages :+= message
          }
        }
      }
      def snapshot: Vector[ProtocolMessage] = output.synchronized(messages)
      val factory: () => TestSuite = kind match {
        case "cats" => () => new CatsChannelSuite(stats)
        case "zio" => () => new ZIOChannelSuite(stats)(using zioDefaults)
      }
      val application = new TestApplication(run, identity, Vector(factory), context, output)
      val request = RunRequest(identity, Selection.All, RunOverrides(Vector.empty, Vector.empty, MemoizationOverride.Inherit))
      def bounded[A](phase: String, signal: Future[A]): Future[A] = timed(signal, CancellationTimeout, () => throw new AssertionError(label + "timed out during " + phase), runtime, context)
      application.accept(ProtocolMessage.Discover(run, identity.build, identity.target)).flatMap { _ =>
        verify(label + "discovery does not load application resources or bodies", snapshot.last.asInstanceOf[ProtocolMessage.Discovered].catalogue.tests.size == 2 && stats.acquired.get() == 0 && stats.bodies.get() == 0)
        application.accept(ProtocolMessage.Request(RequestOperation.Plan, run, request))
      }.flatMap { _ =>
        verify(label + "inspection leaves the actual memoized resource unacquired", snapshot.last.isInstanceOf[ProtocolMessage.Planned] && stats.acquired.get() == 0 && stats.bodies.get() == 0)
        val execution = application.accept(ProtocolMessage.Request(RequestOperation.Execute, run, request))
        val checked = bounded("interruption and finalizer entry", Future.sequence(Vector(stats.interrupted.future, stats.releaseEntered.future))).flatMap { _ =>
          verify(label + "interrupts a real active body without opening its gate", stats.entered.isCompleted && !stats.bodyGate.isCompleted && stats.bodies.get() == 2 && writesAfterLoss.get() == 1)
          verify(label + "waits for actual Lifecycle finalization", !execution.isCompleted && stats.acquired.get() == 1 && stats.released.get() == 0)
          verify(label + "emits no terminal frames on the lost channel", !snapshot.exists { case ProtocolMessage.Event(_, _: RunEvent.Finished) => true; case _: ProtocolMessage.Completed => true; case _ => false })
          // Settle the cancelled Future's callback while its effect runtime is still held alive.
          val _ = stats.bodyGate.trySuccess(())
          require(stats.releaseGate.trySuccess(()), "DI channel finalizer gate opened twice")
          bounded("command completion", execution.failed).map { cause =>
            verify(label + "retains the original delivery exception after cleanup", cause eq failedWrite)
            verify(label + "releases the memoized resource once", stats.acquired.get() == 1 && stats.released.get() == 1 && stats.releaseCompleted.isCompleted)
            verify(label + "does not retry the lost channel", writesAfterLoss.get() == 1 && !snapshot.exists(_.isInstanceOf[ProtocolMessage.Completed]))
            println("DISTAGE_APPLICATION_CHANNEL_LOSS kind=" + kind + " active=interrupted acquired=" + stats.acquired.get() + " released=" + stats.released.get() + " error=original writes=stopped")
          }
        }
        checked.transformWith { result =>
          if (!execution.isCompleted) { val _ = stats.bodyGate.trySuccess(()) }
          val _ = stats.releaseGate.trySuccess(())
          bounded("cleanup", execution).transformWith(_ => Future.fromTry(result))
        }
      }
    } }
  }

  private def timed[A](signal: Future[A], duration: FiniteDuration, timeout: () => A, runtime: QuasiIORunner[MiniBIOAsync[Throwable, _]], context: ExecutionContext): Future[A] = {
    implicit val ec: ExecutionContext = context
    val (timer, cancel) = runtime.runFutureInterruptible(MiniBIOAsync.WeakAsyncForMiniBIOAsync.sleep(duration))
    Future.firstCompletedOf(Vector(signal, timer.map(_ => timeout()))).transformWith { result =>
      cancel().flatMap(_ => timer.transformWith(_ => Future.fromTry(result)))
    }
  }

  private final class ParallelRecordingSink extends EventSink {
    private var recorded = Vector.empty[ProtocolMessage.Event]
    override def accept(event: ProtocolMessage.Event): Unit = synchronized { recorded :+= event }
    def events: Vector[ProtocolMessage.Event] = synchronized(recorded)
  }

  private final class RecordingSink(stats: Statistics) extends EventSink {
    private var recorded = Vector.empty[ProtocolMessage.Event]
    def events: Vector[ProtocolMessage.Event] = synchronized(recorded)
    override def accept(event: ProtocolMessage.Event): Unit = synchronized {
      event.event match {
        case _: RunEvent.Finished => require(stats.released.get() == 1, "Cancellation Finished preceded resource release")
        case _ => ()
      }
      recorded :+= event
    }
  }

  private def configuration[F[_]: TagK](stats: Statistics, parallelEnvs: TestConfig.Parallelism, F: QuasiIO[F], FA: QuasiAsync[F]): TestConfig = TestConfig.empty.copy(
    pluginConfig = PluginConfig.const(new PluginDef {
      make[Resource].fromResource {
        () => Lifecycle.make[F, Resource](F.maybeSuspend { val _ = stats.acquired.incrementAndGet(); new Resource }) { _ =>
          F.flatMap(F.maybeSuspend { require(stats.releaseEntered.trySuccess(()), "Cancellation finalizer entered twice") }) { _ =>
            F.flatMap(FA.fromFuture(stats.releaseGate.future)) { _ => F.maybeSuspend {
              val _ = stats.released.incrementAndGet()
              require(stats.releaseCompleted.trySuccess(()), "Cancellation resource released twice")
              if (stats.failRelease) throw stats.releaseFailure
            } }
          }
        }
      }
    }),
    memoizationRoots = TestConfig.PriorityAxisDIKeys.fromSet(Set(DIKey[Resource])),
    activationStrategy = TestActivationStrategy.IgnoreConfig,
    parallelEnvs = parallelEnvs,
    parallelSuites = TestConfig.Parallelism.Sequential,
    parallelTests = TestConfig.Parallelism.Sequential,
    logLevel = Log.Level.Error,
  )

  private final class CatsSuite(stats: Statistics, parallelEnvs: TestConfig.Parallelism) extends Spec1[IO] {
    override protected def config: TestConfig = configuration(stats, parallelEnvs, QuasiIO[IO], QuasiAsync[IO])
    "before cancellation" in { (_: Resource) => IO { val _ = stats.bodies.incrementAndGet(); () } }
    "active cancellation" in { (_: Resource) =>
      IO { val _ = stats.bodies.incrementAndGet(); require(stats.entered.trySuccess(())) }
        .flatMap(_ => IO.fromFutureCancelable(IO.pure((stats.bodyGate.future, IO.unit))))
        .onCancel(IO { require(stats.interrupted.trySuccess(()), "IO body interrupted twice") })
    }
    "after cancellation" in { (_: Resource) => IO { val _ = stats.bodies.incrementAndGet(); () } }
  }

  private final class ZIOSuite(stats: Statistics, parallelEnvs: TestConfig.Parallelism)(implicit defaults: DefaultModule2[zio.IO]) extends Spec2[zio.IO] {
    override protected def config: TestConfig = configuration(stats, parallelEnvs, QuasiIO[zio.Task], QuasiAsync[zio.Task])
    "before cancellation" in { (_: Resource) => ZIO.succeed { val _ = stats.bodies.incrementAndGet(); () } }
    "active cancellation" in { (_: Resource) =>
      ZIO.succeed { val _ = stats.bodies.incrementAndGet(); require(stats.entered.trySuccess(())) }
        .flatMap(_ => ZIO.fromFuture(_ => stats.bodyGate.future))
        .onInterrupt(ZIO.succeed { require(stats.interrupted.trySuccess(()), "ZIO body interrupted twice") })
    }
    "after cancellation" in { (_: Resource) => ZIO.succeed { val _ = stats.bodies.incrementAndGet(); () } }
  }

  private final class CatsChannelSuite(stats: Statistics) extends Spec1[IO] {
    override protected def config: TestConfig = configuration(stats, TestConfig.Parallelism.Sequential, QuasiIO[IO], QuasiAsync[IO]).copy(parallelTests = TestConfig.Parallelism.Fixed(2))
    "active channel loss" in { (_: Resource) =>
      IO { val _ = stats.bodies.incrementAndGet(); require(stats.entered.trySuccess(())) }
        .flatMap(_ => IO.fromFutureCancelable(IO.pure((stats.bodyGate.future, IO.unit))))
        .onCancel(IO { require(stats.interrupted.trySuccess(()), "Channel IO body interrupted twice") })
    }
    "trigger channel loss" in { (_: Resource) => IO.fromFuture(IO.pure(stats.entered.future)).map { _ => val _ = stats.bodies.incrementAndGet(); () } }
  }

  private final class ZIOChannelSuite(stats: Statistics)(implicit defaults: DefaultModule2[zio.IO]) extends Spec2[zio.IO] {
    override protected def config: TestConfig = configuration(stats, TestConfig.Parallelism.Sequential, QuasiIO[zio.Task], QuasiAsync[zio.Task]).copy(parallelTests = TestConfig.Parallelism.Fixed(2))
    "active channel loss" in { (_: Resource) =>
      ZIO.succeed { val _ = stats.bodies.incrementAndGet(); require(stats.entered.trySuccess(())) }
        .flatMap(_ => ZIO.fromFuture(_ => stats.bodyGate.future))
        .onInterrupt(ZIO.succeed { require(stats.interrupted.trySuccess(()), "Channel ZIO body interrupted twice") })
    }
    "trigger channel loss" in { (_: Resource) => ZIO.fromFuture(_ => stats.entered.future).map { _ => val _ = stats.bodies.incrementAndGet(); () } }
  }
}
