package com.github.pshirshov.izumi.distage.testkit

import com.github.pshirshov.izumi.distage.app.BootstrapConfig
import com.github.pshirshov.izumi.distage.model.definition.BindingTag
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse.TagK
import com.github.pshirshov.izumi.distage.plugins.MergedPlugins
import com.github.pshirshov.izumi.distage.plugins.load.PluginLoaderDefaultImpl.PluginConfig
import com.github.pshirshov.izumi.distage.plugins.merge.ConfigurablePluginMergeStrategy.PluginMergeConfig
import com.github.pshirshov.izumi.distage.plugins.merge.{ConfigurablePluginMergeStrategy, PluginMergeStrategy}
import com.github.pshirshov.izumi.distage.roles.services.{PluginSource, PluginSourceImpl}
import com.github.pshirshov.izumi.logstage.api.IzLogger
import distage.config.AppConfig

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
    val roles = loadRoles(logger)
    val plugins = makePluginLoader(bootstrapConfig).load()
    val mergeStrategy = makeMergeStrategy(logger)
    val defBs: MergedPlugins = mergeStrategy.merge(plugins.bootstrap)
    val defApp: MergedPlugins = mergeStrategy.merge(plugins.app)
    val loadedBsModule = defBs.definition
    val loadedAppModule = defApp.definition
    TestEnvironment(
      loadedBsModule,
      loadedAppModule,
      roles,
    )
  }

  protected def disabledTags: BindingTag.Expressions.Expr = BindingTag.Expressions.False

  protected def makeMergeStrategy(lateLogger: IzLogger): PluginMergeStrategy = {
    new ConfigurablePluginMergeStrategy(PluginMergeConfig(
      disabledTags
      , Set.empty
      , Set.empty
      , Map.empty
    ))
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
