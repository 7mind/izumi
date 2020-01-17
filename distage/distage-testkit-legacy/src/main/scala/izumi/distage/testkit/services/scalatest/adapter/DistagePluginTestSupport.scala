package izumi.distage.testkit.services.scalatest.adapter

import distage.SafeType
import izumi.distage.model.definition.Activation
import izumi.distage.model.definition.StandardAxis._
import izumi.distage.plugins.PluginConfig
import izumi.distage.plugins.load.PluginLoader
import izumi.distage.testkit.TestConfig
import izumi.distage.testkit.services.dstest.{DistageTestEnv, TestEnvironment}
import izumi.fundamentals.platform.language.unused
import izumi.fundamentals.reflection.Tags.TagK
import izumi.logstage.api.IzLogger

@deprecated("Use dstest", "2019/Jul/18")
abstract class DistagePluginTestSupport[F[_] : TagK] extends DistageTestSupport[F] with DistageTestEnv {

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
    val config = TestConfig(bootstrapConfig.pluginConfig, bootstrapConfig.bootstrapPluginConfig.getOrElse(PluginConfig.empty), activation)
    loadEnvironment(logger, config, makePluginLoader(bootstrapConfig))
  }

  protected def activation: Activation = {
    Activation(Env -> Env.Test)
  }

  protected def memoizationContextId: MemoizationContextId = {
    MemoizationContextId.PerRuntimeAndActivationAndBsconfig[F](bootstrapConfig, activation, SafeType.getK[F])
  }

  protected def bootstrapConfig: BootstrapConfig = {
    BootstrapConfig(
      pluginConfig = PluginConfig.cached(pluginPackages),
      bootstrapPluginConfig = pluginBootstrapPackages.map(p => PluginConfig.cached(p)),
    )
  }

  protected def makePluginLoader(@unused bootstrapConfig: BootstrapConfig): PluginLoader = PluginLoader()

}



