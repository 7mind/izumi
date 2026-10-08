package izumi.distage.testkit.runner.di

import cats.effect.IO
import distage.{DefaultModule2, DIKey, TagK}
import izumi.distage.modules.DefaultModule
import izumi.distage.plugins.{PluginConfig, PluginDef}
import izumi.distage.testkit.model.{TestActivationStrategy, TestConfig}
import izumi.distage.testkit.protocol.*
import izumi.distage.testkit.runner.*
import izumi.distage.testkit.runner.spec.{Spec1, Spec2, SpecIdentity}
import izumi.functional.lifecycle.Lifecycle
import izumi.functional.quasi.QuasiIO
import izumi.fundamentals.platform.functional.Identity
import zio.ZIO

import scala.concurrent.{ExecutionContext, Future}

private[di] object SpecInterruptionFixtures {
  private final class Resource
  private final class Statistics extends ResourceStatistics {
    val failures = Vector(
      new InterruptedException("first independent body interruption"),
      new InterruptedException("second independent body interruption"),
      new IllegalStateException("independent ordinary body failure"),
    )
  }

  def run(context: ExecutionContext, verify: (String, Boolean) => Unit): Future[Unit] = {
    implicit val ec: ExecutionContext = context
    val zioDefaults = implicitly[DefaultModule[zio.Task]]
    val cases = Vector("cats", "zio", "identity").flatMap(kind => Vector("first", "repeated").map(kind -> _))
    cases.foldLeft(Future.successful(())) { case (before, (kind, repeat)) => before.flatMap { _ =>
      val stats = new Statistics
      val identity = CatalogueIdentity(BuildId("spec-interruption"), BuildTargetId("spec-target"), CatalogueId(kind + "-" + repeat))
      val factory: () => TestSuite = kind match {
        case "identity" => () => new IdentitySuite(stats)
        case "cats" => () => new CatsSuite(stats)
        case "zio" => () => new ZIOSuite(stats)(using zioDefaults)
      }
      val expectedTests = if (kind == "identity") stats.failures.size else 1
      var events = Vector.empty[ProtocolMessage.Event]
      val sink = new EventSink { override def accept(event: ProtocolMessage.Event): Unit = synchronized {
        event.event match {
          case _: RunEvent.Finished => require(stats.acquired.get() == stats.released.get(), "Interruption completion must follow resource release")
          case _ => ()
        }
        events :+= event
      } }
      val session = new RunSession(identity, Vector(factory), context, sink)
      val catalogue = session.discover().fold(failure => throw new IllegalStateException(failure.message), value => value)
      val label = kind + " " + repeat + " interruption "
      verify(label + "discovery retains every structured identity and source", catalogue.tests.size == expectedTests && catalogue.tests.forall(_.location.isInstanceOf[SourceLocation.Known]))
      verify(label + "discovery suspends resources, bodies and reports", stats.acquired.get() == 0 && stats.released.get() == 0 && stats.bodies.get() == 0 && events.isEmpty)
      val request = RunRequest(identity, Selection.All, RunOverrides(Vector.empty, Vector.empty, MemoizationOverride.Inherit))
      session.execute(RunId(repeat), request).map { outcome =>
        println("DISTAGE_SPEC_INTERRUPTION kind=" + kind + " repeat=" + repeat + " results=" + outcome.results.size + " failures=" + outcome.failures.map(failure => failure.phase.toString + ":" + failure.message) + " bodies=" + stats.bodies.get() + " acquired=" + stats.acquired.get() + " released=" + stats.released.get())
        verify(label + "reports every selected terminal identity once", outcome.results.size == expectedTests && outcome.results.map(_.id).toSet == catalogue.tests.map(_.id).toSet)
        verify(label + "preserves body failures without a session cancellation request", !outcome.cancelled && !outcome.successful && outcome.results.forall(result => result.status == TestStatus.Failed && result.failure.exists(_.phase == FailurePhase.Test)))
        if (kind == "identity") {
          val expectedFailures = stats.failures.zipWithIndex.map { case (cause, index) => Vector("body failure " + index) -> (cause.getClass.getName -> cause.getMessage) }.toMap
          val actualFailures = outcome.results.flatMap(result => result.failure.map(failure => result.id.path -> (failure.exceptionClass -> failure.message))).toMap
          verify(label + "preserves each original interruption and ordinary failure on its identity", actualFailures == expectedFailures)
          verify(label + "does not manufacture a duplicate-report finalization failure", outcome.failures.isEmpty)
        } else {
          verify(label + "retains the effect interruption failure payload", outcome.results.forall(_.failure.exists(failure => failure.exceptionClass.nonEmpty && failure.message.nonEmpty)))
          val expectedRunFailures = if (kind == "cats") 1 else 0
          verify(label + "preserves incoming runtime failures without duplicate reporting", outcome.failures.size == expectedRunFailures && outcome.failures.forall(failure => failure.phase == FailurePhase.Finalization && !failure.message.contains("repeated test completion")))
        }
        verify(label + "runs each selected body and releases its shared resource", stats.bodies.get() == expectedTests && stats.acquired.get() == 1 && stats.released.get() == 1)
        val started = events.collect { case ProtocolMessage.Event(_, RunEvent.TestStarted(_, test)) => test }
        val completed = events.collect { case ProtocolMessage.Event(_, RunEvent.TestCompleted(_, result)) => result.id }
        verify(label + "emits one start and completion for each logical identity", started.size == expectedTests && started.toSet == catalogue.tests.map(_.id).toSet && completed.size == expectedTests && completed.toSet == started.toSet)
        verify(label + "emits exactly one terminal run after cleanup", events.collect { case ProtocolMessage.Event(_, RunEvent.Finished(_, result)) => result } == Vector(outcome) && events.last.event == RunEvent.Finished(outcome.run, outcome))
        verify(label + "retains contiguous event ordinals", events.map(_.sequence) == events.indices.map(_.toLong).toVector)
      }
    } }
  }

  private def configuration[F[_]: TagK](stats: Statistics, F: QuasiIO[F]): TestConfig = TestConfig.empty.copy(
    pluginConfig = PluginConfig.const(new PluginDef {
      make[Resource].fromResource {
        () => Lifecycle.make[F, Resource](F.maybeSuspend { val _ = stats.acquired.incrementAndGet(); new Resource }) {
          _ => F.maybeSuspend { val _ = stats.released.incrementAndGet(); () }
        }
      }
    }),
    memoizationRoots = TestConfig.PriorityAxisDIKeys.fromSet(Set(DIKey[Resource])),
    activationStrategy = TestActivationStrategy.IgnoreConfig,
    parallelEnvs = TestConfig.Parallelism.Sequential,
    parallelSuites = TestConfig.Parallelism.Sequential,
    parallelTests = TestConfig.Parallelism.Sequential,
  )

  private final class IdentitySuite(stats: Statistics) extends SpecIdentity {
    override protected def config: TestConfig = configuration(stats, QuasiIO[Identity])
    stats.failures.zipWithIndex.foreach { case (cause, index) =>
      ("body failure " + index) in { (_: Resource) =>
        val _ = stats.bodies.incrementAndGet()
        throw cause
      }
    }
  }

  private final class CatsSuite(stats: Statistics) extends Spec1[IO] {
    override protected def config: TestConfig = configuration(stats, QuasiIO[IO])
    "self interruption" in { (_: Resource) =>
      IO { val _ = stats.bodies.incrementAndGet(); () }.flatMap(_ => IO.canceled)
    }
  }

  private final class ZIOSuite(stats: Statistics)(implicit defaults: DefaultModule2[zio.IO]) extends Spec2[zio.IO] {
    override protected def config: TestConfig = configuration(stats, QuasiIO[zio.Task])
    "self interruption" in { (_: Resource) =>
      ZIO.succeed { val _ = stats.bodies.incrementAndGet(); () }.flatMap(_ => ZIO.interrupt)
    }
  }
}
