package izumi.distage.framework.services

import distage.{BootstrapModule, TagK}
import izumi.distage.config.AppConfigModule
import izumi.distage.config.model.AppConfig
import izumi.distage.effect.modules.IdentityDIEffectModule
import izumi.distage.framework.activation.PruningPlanMergingPolicy
import izumi.distage.framework.config.PlanningOptions
import izumi.distage.framework.services.ResourceRewriter.RewriteRules
import izumi.distage.model.definition.{Activation, BootstrapModuleDef, Module}
import izumi.distage.model.planning.{PlanMergingPolicy, PlanningHook}
import izumi.distage.planning.AutoSetModule
import izumi.distage.planning.extensions.GraphDumpBootstrapModule
import izumi.distage.roles.meta.RolesInfo
import izumi.distage.roles.model.{AbstractRole, ActivationInfo}
import izumi.fundamentals.platform.cli.model.raw.RawAppArgs
import izumi.logstage.api.IzLogger
import izumi.logstage.distage.LogstageModule

trait ModuleProvider {
  def bootstrapModules(): Seq[BootstrapModule]
  def appModules(): Seq[Module]
}

object ModuleProvider {

  class Impl[F[_]: TagK]
  (
    logger: IzLogger,
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
        make[PlanMergingPolicy].from[PruningPlanMergingPolicy]
      }

      val loggerModule = new LogstageModule(logger.router, true)

      val autosetModule = AutoSetModule()
        .register[AbstractRole[F]]

      val resourceRewriter = new BootstrapModuleDef {
        make[RewriteRules].fromValue(options.rewriteRules)
        many[PlanningHook].add[ResourceRewriter]
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
