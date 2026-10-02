package izumi.distage.testkit.runner.di

import cats.effect.IO
import izumi.distage.plugins.PluginConfig
import izumi.distage.testkit.model.TestConfig
import izumi.distage.testkit.protocol.*
import izumi.distage.testkit.runner.*
import izumi.distage.testkit.runner.spec.{Spec1, Spec2, SpecIdentity, SpecZIO}
import izumi.distage.testkit.spec.TestConfiguration
import izumi.fundamentals.platform.language.Quirks.Discarder
import zio.ZIO

import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.{ExecutionContext, Future}

private[di] object SpecRegistrationFixtures {
  private final val OwnershipAttempts = 32

  private final class Counters {
    val factories = new AtomicInteger(0)
    val configurations = new AtomicInteger(0)
    val effects = new AtomicInteger(0)
    val bodies = new AtomicInteger(0)
    def body(): Unit = bodies.incrementAndGet().discard()
    def untouched: Boolean = configurations.get() == 0 && effects.get() == 0 && bodies.get() == 0
  }

  private trait Configured extends TestConfiguration {
    protected def counters: Counters
    override protected def config: TestConfig = {
      counters.configurations.incrementAndGet().discard()
      TestConfig.empty.copy(pluginConfig = PluginConfig.empty)
    }
  }

  private final class IdentitySuite(override protected val counters: Counters, paths: Vector[String]) extends SpecIdentity with Configured {
    paths.foreach(path => path in { counters.body() })
  }
  private final class UnarySuite(override protected val counters: Counters, paths: Vector[String]) extends Spec1[IO] with Configured {
    paths.foreach(path => path in { counters.effects.incrementAndGet().discard(); IO(counters.body()) })
  }
  private final class BifunctorSuite(override protected val counters: Counters, paths: Vector[String]) extends Spec2[zio.IO] with Configured {
    paths.foreach(path => path in { counters.effects.incrementAndGet().discard(); ZIO.succeed(counters.body()) })
  }
  private final class EnvironmentSuite(override protected val counters: Counters, paths: Vector[String]) extends SpecZIO with Configured {
    paths.foreach(path => path in { counters.effects.incrementAndGet().discard(); ZIO.succeed(counters.body()) })
  }

  private final case class Frontend(name: String, create: (Counters, Vector[String]) => TestSuite)

  def run(context: ExecutionContext, verify: (String, Boolean) => Unit): Future[Unit] = {
    implicit val ec: ExecutionContext = context
    val frontends = Vector(
      Frontend("identity", (counters, paths) => new IdentitySuite(counters, paths)),
      Frontend("unary", (counters, paths) => new UnarySuite(counters, paths)),
      Frontend("bifunctor", (counters, paths) => new BifunctorSuite(counters, paths)),
      Frontend("environment", (counters, paths) => new EnvironmentSuite(counters, paths)),
    )
    frontends.foldLeft(Future.successful(())) { (before, frontend) => before.flatMap { _ =>
      duplicates(frontend, context, verify).flatMap(_ => shared(frontend, context, verify))
    } }
  }

  private def duplicates(frontend: Frontend, context: ExecutionContext, verify: (String, Boolean) => Unit): Future[Unit] = {
    implicit val ec: ExecutionContext = context
    val cases = Vector(
      ("path", Vector(Vector("body", "body")), "Duplicate distage test path"),
      ("suite", Vector(Vector("first"), Vector("second")), "Duplicate suite identities"),
      ("test", Vector(Vector("body"), Vector("body")), "Duplicate distage provider identities"),
    )
    cases.foldLeft(Future.successful(())) { case (before, (kind, paths, message)) => before.flatMap { _ =>
      val counters = new Counters
      var events = Vector.empty[ProtocolMessage.Event]
      val sink = new EventSink { override def accept(event: ProtocolMessage.Event): Unit = synchronized { events :+= event } }
      val identity = catalogueIdentity(frontend.name + "-duplicate-" + kind)
      val factories = paths.map { path => () => { counters.factories.incrementAndGet().discard(); frontend.create(counters, path) } }
      val session = new RunSession(identity, factories, context, sink)
      val first = session.discover()
      val label = frontend.name + " duplicate " + kind
      verify(label + " rejects its explicit discovery invariant", first.left.toOption.exists(failure => failure.phase == FailurePhase.Discovery && failure.message.contains(message)))
      verify(label + " retains its rejection without repeated factories or effects", session.discover() == first && counters.factories.get() == paths.size && counters.untouched && events.isEmpty)
      val request = RunRequest(identity, Selection.All, RunOverrides(Vector.empty, Vector.empty, MemoizationOverride.Inherit))
      session.execute(RunId(label), request).map { outcome =>
        verify(label + " reports rejection without application execution", outcome.results.isEmpty && outcome.failures == first.left.toOption.toVector && counters.untouched && events.map(_.sequence) == Vector(0L, 1L) && events.last.event == RunEvent.Finished(outcome.run, outcome))
      }
    } }
  }

  private def shared(frontend: Frontend, context: ExecutionContext, verify: (String, Boolean) => Unit): Future[Unit] = {
    implicit val ec: ExecutionContext = context
    val counters = new Counters
    val reports = new AtomicInteger(0)
    val sink = new EventSink { override def accept(event: ProtocolMessage.Event): Unit = { val _ = event; reports.incrementAndGet().discard() } }
    (1 to OwnershipAttempts).foldLeft(Future.successful(())) { (before, attempt) => before.flatMap { _ =>
      val suite = frontend.create(counters, Vector("body"))
      val sessions = Vector("first", "second").map { owner =>
        new RunSession(catalogueIdentity(frontend.name + "-" + attempt + "-" + owner), Vector(() => { counters.factories.incrementAndGet().discard(); suite }), context, sink)
      }
      ProviderFixturePlatform.concurrentDiscovery(sessions, context).map { outcomes =>
        require(outcomes.count(_.isRight) == 1, frontend.name + " shared suite was accepted by an invalid number of owners: " + outcomes)
        require(outcomes.flatMap(_.left.toOption).forall(failure => failure.phase == FailurePhase.Discovery && failure.message.contains("Suite instance cannot be shared between sessions")), frontend.name + " shared suite lost its ownership rejection")
        require(outcomes.flatMap(_.toOption).forall(_.tests.size == 1), frontend.name + " shared suite changed its registration")
      }
    } }.map { _ =>
      verify(frontend.name + " shared instance retains one owner per paired discovery", counters.factories.get() == OwnershipAttempts * 2)
      verify(frontend.name + " shared discovery leaves configuration effects and reports suspended", counters.untouched && reports.get() == 0)
      println("DISTAGE_SPEC_SHARED_OWNERS frontend=" + frontend.name + " attempts=" + OwnershipAttempts + " acceptedPerAttempt=1 rejectedPerAttempt=1")
    }
  }

  private def catalogueIdentity(name: String): CatalogueIdentity = CatalogueIdentity(BuildId("spec-registration"), BuildTargetId("spec-target"), CatalogueId(name))
}
