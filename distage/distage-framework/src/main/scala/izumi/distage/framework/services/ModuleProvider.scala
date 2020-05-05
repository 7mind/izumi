package izumi.distage.framework.services

import distage.{BootstrapModule, BootstrapModuleDef, Module}
import izumi.distage.config.AppConfigModule
import izumi.distage.config.model.AppConfig
import izumi.distage.effect.modules.IdentityDIEffectModule
import izumi.distage.framework.config.PlanningOptions
import izumi.distage.framework.model.ActivationInfo
import izumi.distage.framework.services.ResourceRewriter.RewriteRules
import izumi.distage.model.planning.PlanningHook
import izumi.distage.planning.extensions.GraphDumpBootstrapModule
import izumi.distage.roles.model.meta.RolesInfo
import izumi.fundamentals.platform.cli.model.raw.RawAppArgs
import izumi.logstage.api.logger.LogRouter
import izumi.logstage.distage.LogstageModule

trait ModuleProvider {
  def bootstrapModules(): Seq[BootstrapModule]
  def appModules(): Seq[Module]
}

object ModuleProvider {

  class Impl(
    logRouter: LogRouter,
    config: AppConfig,
    roles: RolesInfo,
    options: PlanningOptions,
    args: RawAppArgs,
    activationInfo: ActivationInfo,
  ) extends ModuleProvider {

    def bootstrapModules(): Seq[BootstrapModule] = {
      val roleInfoModule = new BootstrapModuleDef {
        make[RolesInfo].fromValue(roles)
        make[RawAppArgs].fromValue(args)
        make[ActivationInfo].fromValue(activationInfo)
      }

      val loggerModule = new LogstageModule(logRouter, true)

      val resourceRewriter = new BootstrapModuleDef {
        make[RewriteRules]
          .fromValue(options.rewriteRules)
        many[PlanningHook]
          .add[ResourceRewriter]
      }

      val graphvizDumpModule = if (options.addGraphVizDump) new GraphDumpBootstrapModule() else BootstrapModule.empty

      val appConfigModule: BootstrapModule = AppConfigModule(config).morph[BootstrapModule]

      Seq(
        roleInfoModule,
        resourceRewriter,
        loggerModule,
        graphvizDumpModule,
        appConfigModule, // make config available for bootstrap plugins
      )
    }

    def appModules(): Seq[Module] = {
      Seq(
        IdentityDIEffectModule
      )
    }

  }

}
