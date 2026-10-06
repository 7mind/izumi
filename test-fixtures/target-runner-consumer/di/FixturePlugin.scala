package candidate

import distage.{Activation, DIKey}
import izumi.distage.config.model.AppConfig
import izumi.distage.model.definition.StandardAxis.Repo
import izumi.distage.plugins.{PluginConfig, PluginDef}
import izumi.distage.plugins.load.{LoadedPlugins, PluginLoader, PluginLoaderFactory, PluginPackageCache}
import izumi.distage.testkit.model.{TestActivationStrategy, TestConfig}
import izumi.distage.testkit.runner.spec.PluginLoaderFactoryConfiguration
import izumi.distage.testkit.spec.TestConfiguration
import io.circe.{Json, JsonObject}

import candidate.plugins.{FixturePlugin, SharedResource}

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
        val plugins = cache.getOrCompute("candidate-sdk-di", Seq(classOf[FixturePlugin].getName), Nil)(izumi.distage.plugins.StaticPluginLoader.scanCompileTime("candidate.plugins"))
        LoadedPlugins(plugins, config.merges, config.overrides)
      }
    }
  }
}
