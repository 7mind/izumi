package com.github.pshirshov.izumi.distage.testkit

import com.github.pshirshov.izumi.distage.model.definition.Axis.AxisValue
import com.github.pshirshov.izumi.distage.model.definition.{AxisBase, BootstrapModuleDef}
import com.github.pshirshov.izumi.distage.model.planning.PlanMergingPolicy
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse.TagK
import com.github.pshirshov.izumi.distage.plugins.load.PluginLoader.PluginConfig
import com.github.pshirshov.izumi.distage.plugins.merge.{PluginMergeStrategy, SimplePluginMergeStrategy}
import com.github.pshirshov.izumi.distage.roles.BootstrapConfig
import com.github.pshirshov.izumi.distage.roles.model.AppActivation
import com.github.pshirshov.izumi.distage.roles.model.meta.RolesInfo
import com.github.pshirshov.izumi.distage.roles.services.{ActivationParser, PluginSource, PluginSourceImpl, PruningPlanMergingPolicy}
import com.github.pshirshov.izumi.distage.testkit.services.{MemoizationContextId, SyncCache}
import com.github.pshirshov.izumi.fundamentals.platform.language.Quirks
import com.github.pshirshov.izumi.logstage.api.IzLogger
import distage.config.AppConfig
import com.github.pshirshov.izumi.distage.model.definition.StandardAxis._

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

  final protected def loadEnvironment(config: AppConfig, logger: IzLogger): TestEnvironment = {
    if (memoizePlugins) {
      DistagePluginTestSupport.getOrCompute(bootstrapConfig, doLoad(logger, bootstrapConfig))
    } else {
      doLoad(logger, bootstrapConfig)
    }
  }

  private def doLoad(logger: IzLogger, config: BootstrapConfig): TestEnvironment = {
    val roles = loadRoles(logger)
    val plugins = makePluginLoader(config).load()
    val mergeStrategy = makeMergeStrategy(logger)

    val defApp = mergeStrategy.merge(plugins.app)

    val available = ActivationParser.findAvailableChoices(logger, defApp)
    val appActivation = AppActivation(available, activation)
    val defBs = mergeStrategy.merge(plugins.bootstrap) overridenBy new BootstrapModuleDef {
      make[PlanMergingPolicy].from[PruningPlanMergingPolicy]
      make[AppActivation].fromValue(appActivation)
    }
    TestEnvironment(
      defBs,
      defApp,
      roles,
      appActivation,
    )
  }

  protected def memoizePlugins: Boolean = {
    Option(System.getProperty("izumi.distage.testkit.plugins.memoize", "true")).contains("true")
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



object DistagePluginTestSupport extends SyncCache[BootstrapConfig, TestEnvironment] {
  // sbt in nofork mode runs each module in it's own classloader thus we have separate cache per module per run
}
