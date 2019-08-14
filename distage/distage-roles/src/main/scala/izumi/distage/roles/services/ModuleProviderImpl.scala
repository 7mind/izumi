package izumi.distage.roles.services

import izumi.distage.config.model.AppConfig
import izumi.distage.config.{ConfigInjectionOptions, ConfigModule}
import izumi.distage.model.definition.{BootstrapModuleDef, Module, ModuleDef}
import izumi.distage.model.monadic.{DIEffect, DIEffectRunner}
import izumi.distage.model.planning.{PlanMergingPolicy, PlanningHook}
import izumi.distage.planning.AutoSetModule
import izumi.distage.planning.extensions.GraphDumpBootstrapModule
import izumi.distage.roles.model.{AbstractRoleF, AppActivation}
import izumi.distage.roles.model.meta.RolesInfo
import izumi.distage.roles.services.ModuleProviderImpl.ContextOptions
import izumi.distage.roles.services.ResourceRewriter.RewriteRules
import izumi.fundamentals.platform.cli.model.raw.RawAppArgs
import izumi.fundamentals.platform.functional.Identity
import izumi.logstage.api.IzLogger
import izumi.logstage.distage.LogstageModule
import distage.TagK


class ModuleProviderImpl[F[_] : TagK](
                                       logger: IzLogger,
                                       config: AppConfig,
                                       roles: RolesInfo,
                                       options: ContextOptions,
                                       args: RawAppArgs,
                                       activation: AppActivation,
                                     ) extends ModuleProvider[F] {
  def bootstrapModules(): Seq[BootstrapModuleDef] = {
    val rolesModule = new BootstrapModuleDef {
      make[RolesInfo].from(roles)
      make[RawAppArgs].from(args)
      make[AppActivation].from(activation)
      make[PlanMergingPolicy].from[PruningPlanMergingPolicy]
    }

    val loggerModule = new LogstageModule(logger.router, true)

    val autosetModule = AutoSetModule()
      .register[AbstractRoleF[F]]

    val configModule = new ConfigModule(config, options.configInjectionOptions)

    val resourceRewriter = new BootstrapModuleDef {
      make[RewriteRules].from(options.rewriteRules)
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
      condModule(options.addGvDump, new GraphDumpBootstrapModule())
    ).flatten
  }

  def appModules(): Seq[Module] = {
    val baseMod = new ModuleDef {
      addImplicit[DIEffect[Identity]]
      make[DIEffectRunner[Identity]].from(DIEffectRunner.IdentityImpl)
    }

    Seq(
      baseMod
    )
  }

  private def condModule(condition: Boolean, module: => distage.BootstrapModuleDef): Seq[BootstrapModuleDef] = {
    if (condition) {
      Seq(module)
    } else {
      Seq.empty
    }
  }

}


object ModuleProviderImpl {

  case class ContextOptions(
                             addGvDump: Boolean,
                             warnOnCircularDeps: Boolean,
                             rewriteRules: RewriteRules,
                             configInjectionOptions: ConfigInjectionOptions,
                           )

}
