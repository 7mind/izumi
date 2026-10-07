package izumi.distage.testkit.runner.di

import distage.{Activation, Axis, DIKey, ModuleDef}
import izumi.distage.config.model.AppConfig
import izumi.distage.plugins.PluginConfig
import izumi.distage.testkit.model.{TestActivationStrategy, TestConfig, TestEnvironment}
import izumi.distage.testkit.protocol.*
import izumi.distage.testkit.runner.*
import izumi.distage.testkit.runner.impl.services.TestConfigLoader
import izumi.distage.testkit.runner.spec.SpecIdentity
import izumi.functional.lifecycle.Lifecycle
import izumi.fundamentals.platform.functional.Identity
import izumi.fundamentals.platform.language.Quirks.Discarder
import izumi.logstage.api.IzLogger

import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.{ExecutionContext, Future}

private[di] object SpecConfigurationFixtures {
  private object Mode extends Axis {
    case object First extends AxisChoiceDef
    case object Second extends AxisChoiceDef
  }
  private final class Resource
  private final case class Chosen(value: String)

  def run(context: ExecutionContext, verify: (String, Boolean) => Unit): Future[Unit] = {
    implicit val ec: ExecutionContext = context
    val changing = new Statistics
    val changingLoader = new TestConfigLoader {
      override def loadConfig(environment: TestEnvironment, logger: IzLogger): AppConfig = {
        val _ = (environment, logger)
        if (changing.loads.incrementAndGet() == 1) changing.first else changing.second
      }
    }
    Vector(Mode.First, Mode.Second).zipWithIndex.foldLeft(Future.successful(())) { case (before, (expected, owner)) => before.flatMap { _ =>
      exercise("changing-" + owner, Vector(expected), changing, changingLoader, context, owner, owner + 1, verify)
    } }.flatMap { _ =>
      val sensitive = new Statistics
      val sensitiveLoader = new TestConfigLoader {
        override def loadConfig(environment: TestEnvironment, logger: IzLogger): AppConfig = {
          val _ = logger
          sensitive.loads.incrementAndGet().discard()
          sensitive.snapshot(environment.activation.activeChoices(Mode))
        }
      }
      exercise("activation-sensitive", Vector(Mode.First, Mode.Second), sensitive, sensitiveLoader, context, 0, 2, verify)
    }
  }

  private def exercise(
    name: String,
    choices: Vector[Axis.AxisChoice],
    stats: Statistics,
    loader: TestConfigLoader,
    context: ExecutionContext,
    before: Int,
    expected: Int,
    verify: (String, Boolean) => Unit,
  ): Future[Unit] = {
    implicit val ec: ExecutionContext = context
    val identity = CatalogueIdentity(BuildId("spec-config"), BuildTargetId("spec-target"), CatalogueId(name))
    val factories = choices.map { choice =>
      val factory: () => TestSuite = () => new TestSuite {
        override def register(registration: RegistrationContext): RegisteredSuite = {
          registration.provider(ProviderId("distage"), () => new DistageExecutionProvider(registration.executionContext, loader, DistageRunnerOptions(false, false))).discard()
          new ConfiguredSuite(choice, stats).register(registration)
        }
      }
      factory
    }
    val session = new RunSession(identity, factories, context, FixtureSupport.silentSink())
    val catalogue = session.discover().fold(failure => throw new IllegalStateException(failure.message), value => value)
    verify(name + " discovery suspends the injected configuration loader", stats.loads.get() == before && stats.acquired.get() == before && stats.bodies.get() == before)
    val request = RunRequest(identity, Selection.All, RunOverrides(Vector.empty, Vector.empty, MemoizationOverride.Inherit))
    val resolved = session.resolve(request).fold(failure => throw new IllegalStateException(failure.message), value => value)
    verify(name + " resolves each original environment from its own loaded snapshot", stats.loads.get() == expected && stats.acquired.get() == before && resolved.tests.map(_.settings.axes).toSet == choices.map(choice => Vector(protocolChoice(choice))).toSet)
    session.execute(RunId(name), request).map { outcome =>
      verify(name + " retains configuration through planning and execution", stats.loads.get() == expected && stats.matches.get() == expected)
      verify(name + " provisions and releases the configuration-specific scopes", stats.acquired.get() == expected && stats.released.get() == expected && stats.bodies.get() == expected)
      verify(name + " execution agrees with the resolved identities", outcome.successful && outcome.results.map(_.id).toSet == catalogue.tests.map(_.id).toSet && outcome.results.size == choices.size)
      println("DISTAGE_SPEC_CONFIG_SNAPSHOT name=" + name + " loads=" + stats.loads.get() + " acquired=" + stats.acquired.get() + " released=" + stats.released.get() + " bodies=" + stats.bodies.get())
    }
  }

  private def protocolChoice(choice: Axis.AxisChoice): AxisChoice = AxisChoice(AxisId(Mode.name), AxisValue(choice.value))

  private final class Statistics {
    val loads = new AtomicInteger(0)
    val acquired = new AtomicInteger(0)
    val released = new AtomicInteger(0)
    val bodies = new AtomicInteger(0)
    val matches = new AtomicInteger(0)
    val first: AppConfig = ProviderFixturePlatform.activationConfig(protocolChoice(Mode.First))
    val second: AppConfig = ProviderFixturePlatform.activationConfig(protocolChoice(Mode.Second))
    def snapshot(choice: Axis.AxisChoice): AppConfig = if (choice == Mode.First) first else second
    val definitions: distage.Module = new ModuleDef {
      make[Chosen].tagged(Mode.First).fromValue(Chosen(Mode.First.value))
      make[Chosen].tagged(Mode.Second).fromValue(Chosen(Mode.Second.value))
      make[Resource].fromResource(() => Lifecycle.make[Identity, Resource] { acquired.incrementAndGet().discard(); new Resource } { _ => released.incrementAndGet().discard() })
    }
  }

  private final class ConfiguredSuite(choice: Axis.AxisChoice, stats: Statistics) extends SpecIdentity {
    override protected def distageSuiteId: izumi.distage.testkit.model.SuiteId = izumi.distage.testkit.model.SuiteId("config-snapshot-" + choice.value)
    override protected def config: TestConfig = TestConfig.empty.copy(
      pluginConfig = PluginConfig.constUnchecked(stats.definitions),
      activation = Activation(Mode -> choice),
      activationStrategy = TestActivationStrategy.LoadConfig(ignoreUnknown = false, warnUnset = false),
      memoizationRoots = TestConfig.PriorityAxisDIKeys.fromSet(Set(DIKey[Resource])),
    )
    "configuration" in { (chosen: Chosen, config: AppConfig, resource: Resource) =>
      require(resource != null)
      stats.bodies.incrementAndGet().discard()
      if (chosen.value == choice.value && (config eq stats.snapshot(choice))) stats.matches.incrementAndGet().discard()
    }
  }
}
