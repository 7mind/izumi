package izumi.distage.framework.services

import distage.{BootstrapModule, TagK}
import izumi.distage.config.AppConfigModule
import izumi.distage.config.model.AppConfig
import izumi.distage.effect.modules.IdentityDIEffectModule
import izumi.distage.framework.activation.PruningPlanMergingPolicyLoggedImpl
import izumi.distage.framework.config.PlanningOptions
import izumi.distage.framework.model.ActivationInfo
import izumi.distage.framework.services.ResourceRewriter.RewriteRules
import izumi.distage.model.definition.{Activation, BootstrapModuleDef, Module}
import izumi.distage.model.planning.{PlanMergingPolicy, PlanningHook}
import izumi.distage.planning.AutoSetModule
import izumi.distage.planning.extensions.GraphDumpBootstrapModule
import izumi.distage.roles.model.AbstractRole
import izumi.distage.roles.model.meta.RolesInfo
import izumi.fundamentals.platform.cli.model.raw.RawAppArgs
import izumi.logstage.api.logger.LogRouter
import izumi.logstage.distage.LogstageModule

trait ModuleProvider {
  def bootstrapModules(): Seq[BootstrapModule]
  def appModules(): Seq[Module]
}

object ModuleProvider {

  class Impl[F[_]: TagK]
  (
    logRouter: LogRouter,
    config: AppConfig,
    roles: RolesInfo,
    options: PlanningOptions,
    args: RawAppArgs,
    activationInfo: ActivationInfo,
    activation: Activation,
  ) extends ModuleProvider {

    def bootstrapModules(): Seq[BootstrapModule] = {
      val rolesModule = new BootstrapModuleDef {
        make[RolesInfo].fromValue(roles)
        make[RawAppArgs].fromValue(args)
        make[ActivationInfo].fromValue(activationInfo)
        make[Activation].fromValue(activation)
        make[PlanMergingPolicy].from[PruningPlanMergingPolicyLoggedImpl]
      }

      val loggerModule = new LogstageModule(logRouter, true)

      val autosetModule = AutoSetModule()
        .register[AbstractRole[F]]

      val resourceRewriter = new BootstrapModuleDef {
        make[RewriteRules].fromValue(options.rewriteRules)
        many[PlanningHook]
          .add[ResourceRewriter]
      }

      Seq(
        Seq(
          autosetModule,
          rolesModule,
          resourceRewriter,
          loggerModule,
        ),
        if (options.addGraphVizDump) Seq(new GraphDumpBootstrapModule()) else Seq.empty,
      ).flatten
    }

    def appModules(): Seq[Module] = {
      val configModule = new AppConfigModule(config)
      Seq(
        configModule,
        IdentityDIEffectModule,
      )
    }
  }

}
