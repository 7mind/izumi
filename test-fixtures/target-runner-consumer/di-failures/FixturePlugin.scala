package candidate

import distage.{Activation, DIKey}
import izumi.distage.config.model.AppConfig
import izumi.distage.model.definition.StandardAxis.Repo
import izumi.distage.plugins.{PluginConfig, PluginDef}
import izumi.distage.plugins.load.{LoadedPlugins, PluginLoader, PluginLoaderFactory, PluginPackageCache}
import izumi.distage.testkit.model.{TestActivationStrategy, TestConfig}
import izumi.distage.testkit.runner.spec.PluginLoaderFactoryConfiguration
import izumi.distage.testkit.spec.TestConfiguration
import izumi.functional.lifecycle.Lifecycle
import izumi.fundamentals.platform.functional.Identity
import io.circe.{Json, JsonObject}
import izumi.fundamentals.platform.uuid.IzUUID

final case class ImplementationRevision(value: String)
final class SharedResource(val id: String, val revision: String, val snapshot: String, val repo: String)

final class FixturePlugin extends PluginDef {
  println("SDK_DI_PLUGIN revision=" + implementationRevision)
  make[ImplementationRevision].fromValue(ImplementationRevision(implementationRevision))
  make[SharedResource].tagged(Repo.Dummy).fromResource { (version: ImplementationRevision, config: AppConfig) => resource("dummy", version, config) }
  make[SharedResource].tagged(Repo.Prod).fromResource { (version: ImplementationRevision, config: AppConfig) => resource("prod", version, config) }

  private def implementationRevision: String = "one"

  private def resource(repo: String, version: ImplementationRevision, config: AppConfig): Lifecycle[Identity, SharedResource] =
    Lifecycle.make[Identity, SharedResource] {
      val snapshot = config.config("snapshot").flatMap(_.asString).getOrElse(throw new IllegalStateException("SDK configuration snapshot is missing"))
      val value = new SharedResource(IzUUID.generateTimeUUID().toString, version.value, snapshot, repo)
      println("SDK_DI_ACQUIRE owner=" + value.id + " revision=" + value.revision + " snapshot=" + value.snapshot + " repo=" + value.repo)
      value
    } { value =>
      println("SDK_DI_RELEASE owner=" + value.id)
      if (value.snapshot == "release-failure") throw new IllegalStateException("SDK_DI_RELEASE_FAILURE owner=" + value.id)
    }
}

trait Configured extends TestConfiguration with PluginLoaderFactoryConfiguration {
  abstract override protected def config: TestConfig = super.config.copy(
    pluginConfig = PluginConfig.empty,
    activation = Activation(Repo -> Repo.Dummy),
    activationStrategy = TestActivationStrategy.IgnoreConfig,
    configOverrides = Some(AppConfig.provided(Platform.overrideConfiguration(JsonObject("snapshot" -> Json.fromString("alpha"))))),
    memoizationRoots = TestConfig.PriorityAxisDIKeys.fromSet(Set(DIKey[SharedResource])),
  )

  override protected def makePluginLoaderFactory(): PluginLoaderFactory = new PluginLoaderFactory {
    override def create(cache: PluginPackageCache): PluginLoader = new PluginLoader {
      override def load(config: PluginConfig): LoadedPlugins = {
        val plugins = cache.getOrCompute("candidate-sdk-di", Seq(classOf[FixturePlugin].getName), Nil)(Seq(new FixturePlugin))
        LoadedPlugins(plugins, config.merges, config.overrides)
      }
    }
  }
}
