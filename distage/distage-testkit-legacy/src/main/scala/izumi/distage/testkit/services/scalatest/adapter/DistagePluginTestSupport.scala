package izumi.distage.testkit.services.scalatest.adapter

import distage.SafeType
import izumi.distage.framework.activation.PruningPlanMergingPolicyLoggedImpl
import izumi.distage.framework.model.{ActivationInfo, BootstrapConfig, PluginSource}
import izumi.distage.framework.services.ActivationInfoExtractor
import izumi.distage.model.definition.StandardAxis._
import izumi.distage.model.definition.{Activation, BootstrapModuleDef, ModuleBase}
import izumi.distage.model.planning.PlanMergingPolicy
import izumi.distage.plugins.PluginConfig
import izumi.distage.plugins.merge.{PluginMergeStrategy, SimplePluginMergeStrategy}
import izumi.distage.roles.model.meta.RolesInfo
import izumi.distage.testkit.DebugProperties
import izumi.distage.testkit.services.PluginsCache
import izumi.distage.testkit.services.PluginsCache.CacheKey
import izumi.distage.testkit.services.dstest.TestEnvironment
import izumi.fundamentals.platform.language.unused
import izumi.fundamentals.reflection.Tags.TagK
import izumi.logstage.api.IzLogger

@deprecated("Use dstest", "2019/Jul/18")
abstract class DistagePluginTestSupport[F[_] : TagK] extends DistageTestSupport[F] {

  /**
    * This may be used as an implementation of [[pluginPackages]] in simple cases.
    *
    * Though it has to be always explicitly specified because this behaviour applied by default
    * would be very obscure.
    */
  protected final def thisPackage: Seq[String] = {
    Seq(this.getClass.getPackage.getName)
  }

  protected def pluginPackages: Seq[String]

  protected def pluginBootstrapPackages: Option[Seq[String]] = None

  override final def loadEnvironment(logger: IzLogger): TestEnvironment = {
    val pluginSource = PluginSource(bootstrapConfig)

    def env(): PluginsCache.CacheValue = {
      val plugins = pluginSource.load()
      val mergeStrategy = makeMergeStrategy(logger)
      val appModule = mergeStrategy.merge(plugins.app)
      val bootstrapModule = mergeStrategy.merge(plugins.bootstrap)
      val availableActivations = ActivationInfoExtractor.findAvailableChoices(logger, appModule)
      PluginsCache.CacheValue(appModule, bootstrapModule, availableActivations)
    }

    val plugins = if (memoizePlugins) {
      val key = Set(bootstrapConfig)
      PluginsCache.Instance.getOrCompute(CacheKey(key), env())
    } else {
      env()
    }

    doLoad(logger, plugins)
  }

  protected final def doLoad(logger: IzLogger, env: PluginsCache.CacheValue): TestEnvironment = {
    val roles = loadRoles(logger)
    val bsModule = env.mergedBsPlugins overridenBy new BootstrapModuleDef {
      make[PlanMergingPolicy].from[PruningPlanMergingPolicyLoggedImpl]
      make[ActivationInfo].fromValue(env.availableActivations)
      make[Activation].fromValue(activation)
    }
    TestEnvironment(
      bsModule = bsModule,
      appModule = env.mergedAppPlugins,
      roles = roles,
      activationInfo = env.availableActivations,
      activation = activation,
      memoizedKeys = Set.empty,
    )
  }

  protected def memoizePlugins: Boolean = {
    import izumi.fundamentals.platform.strings.IzString._

    System
      .getProperty(DebugProperties.`izumi.distage.testkit.plugins.memoize`)
      .asBoolean(true)
  }

  protected def loadRoles(@unused logger: IzLogger): RolesInfo = {
    // For all normal scenarios we don't need roles to setup a test
    RolesInfo(Set.empty, Seq.empty, Seq.empty, Seq.empty, Set.empty)
  }

  protected def activation: Activation = {
    Activation(Env -> Env.Test)
  }

  protected def memoizationContextId: MemoizationContextId = {
    MemoizationContextId.PerRuntimeAndActivationAndBsconfig[F](bootstrapConfig, activation, SafeType.getK[F])
  }

  protected def makeMergeStrategy(@unused lateLogger: IzLogger): PluginMergeStrategy = {
    SimplePluginMergeStrategy
  }

  protected def bootstrapConfig: BootstrapConfig = {
    BootstrapConfig(
      PluginConfig(debug = false, pluginPackages, Seq.empty),
      pluginBootstrapPackages.map(p => PluginConfig(debug = false, p, Seq.empty)),
    )
  }

  protected def makePluginLoader(bootstrapConfig: BootstrapConfig): PluginSource = {
    PluginSource(bootstrapConfig)
  }

}



