package com.github.pshirshov.izumi.distage.roles.services

import com.github.pshirshov.izumi.distage.config.model.AppConfig
import com.github.pshirshov.izumi.distage.config.{ConfigInjectionOptions, ConfigModule}
import com.github.pshirshov.izumi.distage.model.definition.{BootstrapModuleDef, Module, ModuleDef}
import com.github.pshirshov.izumi.distage.model.monadic.{DIEffect, DIEffectRunner}
import com.github.pshirshov.izumi.distage.model.planning.{PlanMergingPolicy, PlanningHook}
import com.github.pshirshov.izumi.distage.planning.AutoSetModule
import com.github.pshirshov.izumi.distage.planning.extensions.GraphDumpBootstrapModule
import com.github.pshirshov.izumi.distage.roles.model.{AbstractRoleF, AppActivation}
import com.github.pshirshov.izumi.distage.roles.model.meta.RolesInfo
import com.github.pshirshov.izumi.distage.roles.services.ModuleProviderImpl.ContextOptions
import com.github.pshirshov.izumi.distage.roles.services.ResourceRewriter.RewriteRules
import com.github.pshirshov.izumi.fundamentals.platform.cli.model.raw.RawAppArgs
import com.github.pshirshov.izumi.fundamentals.platform.functional.Identity
import com.github.pshirshov.izumi.logstage.api.IzLogger
import com.github.pshirshov.izumi.logstage.distage.LogstageModule
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
