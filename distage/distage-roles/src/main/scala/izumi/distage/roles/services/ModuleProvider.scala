package izumi.distage.roles.services

import distage._
import izumi.distage.config.ConfigModule
import izumi.distage.config.model.AppConfig
import izumi.distage.model.definition.{BootstrapModuleDef, Module}
import izumi.distage.model.planning.{PlanMergingPolicy, PlanningHook}
import izumi.distage.monadic.modules.IdentityDIEffectModule
import izumi.distage.planning.AutoSetModule
import izumi.distage.planning.extensions.GraphDumpBootstrapModule
import izumi.distage.roles.config.ContextOptions
import izumi.distage.roles.model.meta.RolesInfo
import izumi.distage.roles.model.{AbstractRoleF, AppActivation}
import izumi.distage.roles.services.ResourceRewriter.RewriteRules
import izumi.fundamentals.platform.cli.model.raw.RawAppArgs
import izumi.logstage.api.IzLogger
import izumi.logstage.distage.LogstageModule

trait ModuleProvider[F[_]] {
  def bootstrapModules(): Seq[BootstrapModuleDef]
  def appModules(): Seq[Module]
}

object ModuleProvider {

  class Impl[F[_]: TagK]
  (
    logger: IzLogger,
    config: AppConfig,
    roles: RolesInfo,
    options: ContextOptions,
    args: RawAppArgs,
    activation: AppActivation,
  ) extends ModuleProvider[F] {

    def bootstrapModules(): Seq[BootstrapModuleDef] = {
      val rolesModule = new BootstrapModuleDef {
        make[RolesInfo].fromValue(roles)
        make[RawAppArgs].fromValue(args)
        make[AppActivation].fromValue(activation)
        make[PlanMergingPolicy].from[PruningPlanMergingPolicy]
      }

      val loggerModule = new LogstageModule(logger.router, true)

      val autosetModule = AutoSetModule()
        .register[AbstractRoleF[F]]

      val configModule = new ConfigModule(config, options.configInjectionOptions)

      val resourceRewriter = new BootstrapModuleDef {
        make[RewriteRules].fromValue(options.rewriteRules)
        many[PlanningHook].add[ResourceRewriter]
      }

      Seq(
        Seq(
          configModule,
          autosetModule,
          rolesModule,
          resourceRewriter,
          loggerModule,
        ),
        if (options.addGraphVizDump) Seq(new GraphDumpBootstrapModule()) else Seq.empty,
      ).flatten
    }

    def appModules(): Seq[Module] = {
      val baseMod = IdentityDIEffectModule

      Seq(baseMod)
    }

  }

}
