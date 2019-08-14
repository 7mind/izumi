package com.github.pshirshov.izumi.distage.testkit.services.dstest

import com.github.pshirshov.izumi.distage.model.definition.{Axis, AxisBase}
import com.github.pshirshov.izumi.distage.plugins.merge.PluginMergeStrategy
import com.github.pshirshov.izumi.distage.roles.BootstrapConfig
import com.github.pshirshov.izumi.distage.roles.model.meta.RolesInfo
import com.github.pshirshov.izumi.distage.roles.services.PluginSource
import com.github.pshirshov.izumi.distage.testkit.services.PluginsCache
import com.github.pshirshov.izumi.logstage.api.IzLogger

trait DistageTestEnvironmentProvider {

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
