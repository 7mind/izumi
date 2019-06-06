package com.github.pshirshov.izumi.distage.testkit

import com.github.pshirshov.izumi.distage.model.definition.Axis.AxisValue
import com.github.pshirshov.izumi.distage.model.definition.StandardAxis._
import com.github.pshirshov.izumi.distage.model.definition.{AxisBase, BootstrapModuleDef, ModuleBase}
import com.github.pshirshov.izumi.distage.model.planning.PlanMergingPolicy
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse.TagK
import com.github.pshirshov.izumi.distage.plugins.load.PluginLoader.PluginConfig
import com.github.pshirshov.izumi.distage.plugins.merge.{PluginMergeStrategy, SimplePluginMergeStrategy}
import com.github.pshirshov.izumi.distage.roles.BootstrapConfig
import com.github.pshirshov.izumi.distage.roles.model.AppActivation
import com.github.pshirshov.izumi.distage.roles.model.meta.RolesInfo
import com.github.pshirshov.izumi.distage.roles.services.{ActivationParser, PluginSource, PluginSourceImpl, PruningPlanMergingPolicy}
import com.github.pshirshov.izumi.distage.testkit.DistagePluginTestSupport.{CacheKey, CacheValue}
import com.github.pshirshov.izumi.distage.testkit.services.{MemoizationContextId, SyncCache}
import com.github.pshirshov.izumi.fundamentals.platform.language.Quirks
import com.github.pshirshov.izumi.logstage.api.IzLogger

abstract class DistagePluginTestSupport[F[_] : TagK] extends DistageTestSupport[F] {

  /**
    * This may be used as an implementation of [[pluginPackages]] in simple cases.
    *
    * Though it has to be always explicitly specified because this behaviour applied by default
    * would be very obscure.
    */
  protected final def thisPackage: Seq[String] = Seq(this.getClass.getPackage.getName)

  protected def pluginPackages: Seq[String]

  protected def pluginBootstrapPackages: Option[Seq[String]] = None

  /**
    * Merge strategy will be applied only once for all the tests with the same bootstrap config when memoization is on
    */
  override protected final def loadEnvironment(logger: IzLogger): TestEnvironment = {
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
      DistagePluginTestSupport.Cache.getOrCompute(CacheKey(config), env())
    } else {
      env()
    }

    doLoad(logger, plugins)
  }

  protected final def doLoad(logger: IzLogger, env: CacheValue): TestEnvironment = {
    val roles = loadRoles(logger)
    val appActivation = AppActivation(env.availableActivations, activation)
    val defBs = env.bsModule overridenBy new BootstrapModuleDef {
      make[PlanMergingPolicy].from[PruningPlanMergingPolicy]
      make[AppActivation].from(appActivation)
    }
    TestEnvironment(
      defBs,
      env.appModule,
      roles,
      appActivation,
    )
  }

  protected def memoizePlugins: Boolean = {
    import com.github.pshirshov.izumi.fundamentals.platform.strings.IzString._

    System.getProperty("izumi.distage.testkit.plugins.memoize")
      .asBoolean(true)
  }

  protected def loadRoles(logger: IzLogger): RolesInfo = {
    Quirks.discard(logger)
    // For all normal scenarios we don't need roles to setup a test
    RolesInfo(Set.empty, Seq.empty, Seq.empty, Seq.empty, Set.empty)
  }

  protected def activation: Map[AxisBase, AxisValue] = Map(Env -> Env.Test)

  protected def memoizationContextId: MemoizationContextId = {
    MemoizationContextId.PerRuntimeAndActivationAndBsconfig[F](bootstrapConfig, activation, distage.SafeType.getK(implicitly[TagK[F]]))
  }

  protected def makeMergeStrategy(lateLogger: IzLogger): PluginMergeStrategy = {
    Quirks.discard(lateLogger)
    SimplePluginMergeStrategy
  }

  protected def bootstrapConfig: BootstrapConfig = {
    BootstrapConfig(
      PluginConfig(debug = false, pluginPackages, Seq.empty),
      pluginBootstrapPackages.map(p => PluginConfig(debug = false, p, Seq.empty)),
    )
  }

  protected def makePluginLoader(bootstrapConfig: BootstrapConfig): PluginSource = {
    new PluginSourceImpl(bootstrapConfig)
  }

}



object DistagePluginTestSupport {
  case class CacheKey(config: BootstrapConfig)
  case class CacheValue(
                         plugins: PluginSource.AllLoadedPlugins,
                         bsModule: ModuleBase,
                         appModule: ModuleBase,
                         availableActivations: Map[AxisBase, Set[AxisValue]],
                       )

  object Cache extends SyncCache[CacheKey, CacheValue] {
    // sbt in nofork mode runs each module in it's own classloader thus we have separate cache per module per run
  }
}
