package com.github.pshirshov.izumi.distage.roles.services

import com.github.pshirshov.izumi.distage.config.model.AppConfig
import com.github.pshirshov.izumi.distage.config.{ConfigInjectionOptions, ConfigModule}
import com.github.pshirshov.izumi.distage.model.definition.{BootstrapModuleDef, Module, ModuleDef}
import com.github.pshirshov.izumi.distage.model.monadic.DIEffect
import com.github.pshirshov.izumi.distage.model.planning.PlanningHook
import com.github.pshirshov.izumi.distage.planning.AutoSetModule
import com.github.pshirshov.izumi.distage.planning.extensions.GraphDumpBootstrapModule
import com.github.pshirshov.izumi.distage.roles._
import com.github.pshirshov.izumi.distage.roles.model.{AbstractRoleF, RolesInfo}
import com.github.pshirshov.izumi.distage.roles.services.ModuleProviderImpl.ContextOptions
import com.github.pshirshov.izumi.distage.roles.services.ResourceRewriter.RewriteRules
import com.github.pshirshov.izumi.fundamentals.platform.functional.Identity
import com.github.pshirshov.izumi.logstage.api.IzLogger
import com.github.pshirshov.izumi.logstage.api.Log.CustomContext
import com.github.pshirshov.izumi.logstage.api.logger.LogRouter
import distage.TagK


class ModuleProviderImpl[F[_] : TagK](
                                       logger: IzLogger,
                                       config: AppConfig,
                                       roles: RolesInfo,
                                       options: ContextOptions,
                                     ) extends ModuleProvider[F] {
  def bootstrapModules(): Seq[BootstrapModuleDef] = {
    val rolesModule = new BootstrapModuleDef {
      make[LogRouter].from(logger.router)
      make[RolesInfo].from(roles)
    }

    val autosetModule = AutoSetModule()
      .register[AbstractRoleF[F]]


    val configModule = new ConfigModule(config, options.configInjectionOptions)

    val resourceRewriter = new BootstrapModuleDef {
      make[IzLogger].from(logger)
      make[RewriteRules].from(options.rewriteRules)
      many[PlanningHook].add[ResourceRewriter]
    }


    Seq(
      configModule,
      autosetModule,
      rolesModule,
      resourceRewriter,
    ) ++ Seq(
      condModule(options.addGvDump, new GraphDumpBootstrapModule())
    ).flatten
  }

  private def condModule(condition: Boolean, module: => distage.BootstrapModuleDef): Seq[BootstrapModuleDef] = {
    if (condition) {
      Seq(module)
    } else {
      Seq.empty
    }
  }

  def appModules(): Seq[Module] = {
    val baseMod = new ModuleDef {
      make[DIEffectRunner[Identity]].from(DIEffectRunner.IdentityDIEffectRunner)
      addImplicit[DIEffect[Identity]]
      make[CustomContext].from(CustomContext.empty)
      make[IzLogger]
    }

    Seq(baseMod)
  }

}


object ModuleProviderImpl {

  case class ContextOptions(
                             addGvDump: Boolean,
                             rewriteRules: RewriteRules,
                             configInjectionOptions: ConfigInjectionOptions,
                           )

}
