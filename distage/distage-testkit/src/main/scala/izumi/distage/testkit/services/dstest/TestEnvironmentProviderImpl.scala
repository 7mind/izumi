package izumi.distage.testkit.services.dstest

import distage.DIKey
import izumi.distage.model.definition.Axis.AxisValue
import izumi.distage.model.definition.{AxisBase, BootstrapModule, ModuleBase}
import izumi.distage.plugins.load.PluginLoader.PluginConfig
import izumi.distage.plugins.merge.{PluginMergeStrategy, SimplePluginMergeStrategy}
import izumi.distage.roles.BootstrapConfig
import izumi.distage.roles.model.AppActivation
import izumi.distage.roles.model.meta.RolesInfo
import izumi.distage.roles.services.{ActivationParser, PluginSource}
import izumi.distage.testkit.services.PluginsCache
import izumi.distage.testkit.services.PluginsCache.{CacheKey, CacheValue}
import izumi.fundamentals.platform.language.Quirks
import izumi.logstage.api.IzLogger

class TestEnvironmentProviderImpl
(
  suiteClass: Class[_],
  override protected val activation: Map[AxisBase, AxisValue],
  protected val memoizedKeys: Set[DIKey],
  protected val bootstrapOverrides: BootstrapModule,
  protected val moduleOverrides: ModuleBase,
  protected val pluginOverrides: Option[Seq[String]],
) extends TestEnvironmentProvider {

  /**
    * Merge strategy will be applied only once for all the tests with the same bootstrap config when memoization is on
    */
  override final def loadEnvironment(logger: IzLogger): TestEnvironment = {
    val config = bootstrapConfig

    def env(): CacheValue = {
      val plugins = makePluginLoader(config).load()
      val mergeStrategy = makeMergeStrategy(logger)
      val defApp = mergeStrategy.merge(plugins.app)
      val bootstrap = mergeStrategy.merge(plugins.bootstrap)
      val availableActivations = ActivationParser.findAvailableChoices(logger, defApp)
      CacheValue(plugins, bootstrap, defApp, availableActivations)
    }

    val plugins = if (memoizePlugins) {
      PluginsCache.Instance.getOrCompute(CacheKey(config), env())
    } else {
      env()
    }

    doLoad(logger, plugins)
  }

  override protected final def doLoad(logger: IzLogger, env: CacheValue): TestEnvironment = {
    val roles = loadRoles(logger)
    val appActivation = AppActivation(env.availableActivations, activation)
    TestEnvironment(
      env.bsModule overridenBy bootstrapOverrides,
      env.appModule overridenBy moduleOverrides,
      roles,
      appActivation,
      memoizedKeys,
    )
  }

  override protected def memoizePlugins: Boolean = {
    import izumi.fundamentals.platform.strings.IzString._

    System.getProperty("izumi.distage.testkit.plugins.memoize")
      .asBoolean(true)
  }

  override protected def loadRoles(logger: IzLogger): RolesInfo = {
    Quirks.discard(logger)
    // For all normal scenarios we don't need roles to setup a test
    RolesInfo(Set.empty, Seq.empty, Seq.empty, Seq.empty, Set.empty)
  }

  override protected def makeMergeStrategy(lateLogger: IzLogger): PluginMergeStrategy = {
    Quirks.discard(lateLogger)
    SimplePluginMergeStrategy
  }

  override protected def bootstrapConfig: BootstrapConfig = {
    BootstrapConfig(
      PluginConfig(debug = false, pluginPackages, Seq.empty),
      pluginBootstrapPackages.map(p => PluginConfig(debug = false, p, Seq.empty)),
    )
  }

  override protected def makePluginLoader(bootstrapConfig: BootstrapConfig): PluginSource = {
    new PluginSource.Impl(bootstrapConfig)
  }

  protected def thisPackage: Seq[String] = Seq(suiteClass.getPackage.getName)

  override protected def pluginPackages: Seq[String] = pluginOverrides getOrElse thisPackage

  override protected def pluginBootstrapPackages: Option[Seq[String]] = None

}
