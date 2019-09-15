package izumi.distage.testkit.services.dstest

import izumi.distage.model.definition.{Axis, AxisBase}
import izumi.distage.plugins.merge.PluginMergeStrategy
import izumi.distage.roles.BootstrapConfig
import izumi.distage.roles.model.meta.RolesInfo
import izumi.distage.roles.services.PluginSource
import izumi.distage.testkit.services.PluginsCache
import izumi.logstage.api.IzLogger

trait TestEnvironmentProvider {

  /**
    * Merge strategy will be applied only once for all the tests with the same bootstrap config when memoization is on
    */
  def loadEnvironment(logger: IzLogger): TestEnvironment

  protected def doLoad(logger: IzLogger, env: PluginsCache.CacheValue): TestEnvironment

  protected def memoizePlugins: Boolean

  protected def loadRoles(logger: IzLogger): RolesInfo

  protected def activation: Map[AxisBase, Axis.AxisValue]

  protected def makeMergeStrategy(lateLogger: IzLogger): PluginMergeStrategy

  protected def bootstrapConfig: BootstrapConfig

  protected def makePluginLoader(bootstrapConfig: BootstrapConfig): PluginSource

  protected def thisPackage: Seq[String]

  protected def pluginPackages: Seq[String]

  protected def pluginBootstrapPackages: Option[Seq[String]]
}
