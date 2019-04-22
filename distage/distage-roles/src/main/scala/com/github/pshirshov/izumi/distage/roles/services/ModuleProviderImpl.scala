package com.github.pshirshov.izumi.distage.roles.services

import java.util.concurrent.ExecutorService

import com.github.pshirshov.izumi.distage.config.ConfigModule
import com.github.pshirshov.izumi.distage.config.model.AppConfig
import com.github.pshirshov.izumi.distage.model.definition.{BootstrapModuleDef, Module, ModuleDef}
import com.github.pshirshov.izumi.distage.model.monadic.DIEffect
import com.github.pshirshov.izumi.distage.planning.AutoSetModule
import com.github.pshirshov.izumi.distage.planning.extensions.GraphDumpBootstrapModule
import com.github.pshirshov.izumi.distage.roles._
import com.github.pshirshov.izumi.fundamentals.platform.functional.Identity
import com.github.pshirshov.izumi.logstage.api.IzLogger
import com.github.pshirshov.izumi.logstage.api.Log.CustomContext
import com.github.pshirshov.izumi.logstage.api.logger.LogRouter
import distage.TagK

class ModuleProviderImpl[F[_] : TagK](
                                       logger: IzLogger,
                                       config: AppConfig,
                                       addGvDump: Boolean,
                                       roles: RolesInfo,
                                     ) extends ModuleProvider[F] {
  def bootstrapModules(): Seq[BootstrapModuleDef] = {
    val rolesModule = new BootstrapModuleDef {
      make[LogRouter].from(logger.router)
      make[RolesInfo].from(roles)
    }

    val autosetModule = AutoSetModule()
      .register[AutoCloseable]
      .register[ExecutorService]
      .register[IntegrationCheck]
      .register[AbstractRoleF[F]]

    val configModule = new ConfigModule(config)

    val maybeDumpGraphModule = if (addGvDump) {
      Seq(new GraphDumpBootstrapModule())
    } else {
      Seq.empty
    }

    Seq(
      configModule,
      autosetModule,
      rolesModule,
    ) ++
      maybeDumpGraphModule
  }

  def appModules(): Seq[Module] = {
    val baseMod = new ModuleDef {
      make[DIEffectRunner[Identity]].from(DIEffectRunner.IdentityDIEffectRunner)
      addImplicit[DIEffect[Identity]]
      make[CustomContext].from(CustomContext.empty)
      make[IzLogger]
      make[Finalizers.CloseablesFinalized].fromResource[Finalizers.CloseablesFinalizer]
      make[Finalizers.ExecutorsFinalized].fromResource[Finalizers.ExecutorsFinalizer]
    }
    Seq(baseMod)
  }

}


