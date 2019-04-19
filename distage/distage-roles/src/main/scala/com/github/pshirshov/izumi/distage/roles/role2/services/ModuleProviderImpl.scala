package com.github.pshirshov.izumi.distage.roles.role2.services

import java.util.concurrent.ExecutorService

import com.github.pshirshov.izumi.distage.config.ConfigModule
import com.github.pshirshov.izumi.distage.config.model.AppConfig
import com.github.pshirshov.izumi.distage.model.definition.{BootstrapModuleDef, Module, ModuleDef}
import com.github.pshirshov.izumi.distage.model.monadic.DIEffect
import com.github.pshirshov.izumi.distage.planning.AutoSetModule
import com.github.pshirshov.izumi.distage.planning.extensions.GraphDumpBootstrapModule
import com.github.pshirshov.izumi.distage.roles._
import com.github.pshirshov.izumi.distage.roles.cli.RoleAppArguments
import com.github.pshirshov.izumi.distage.roles.launcher.{ComponentsLifecycleManagerImpl, RoleStarterImpl}
import com.github.pshirshov.izumi.distage.roles.role2.{DIEffectRunner, RoleAppLauncher}
import com.github.pshirshov.izumi.fundamentals.platform.functional.Identity
import com.github.pshirshov.izumi.logstage.api.IzLogger
import com.github.pshirshov.izumi.logstage.api.Log.CustomContext
import com.github.pshirshov.izumi.logstage.api.logger.LogRouter
import distage.TagK

class ModuleProviderImpl[F[_] : DIEffect : TagK](logger: IzLogger, parameters: RoleAppArguments, config: AppConfig, roleInfo: RolesInfo) extends ModuleProvider[F] {
  private final val roleAutoSetModule = AutoSetModule()
    .register[RoleService]
    .register[AutoCloseable]
    .register[RoleComponent]
    .register[IntegrationCheck]
    .register[ExecutorService]
    .register[RoleTask2[F]]
    .register[RoleService2[F]]

  def bootstrapModules(): Seq[BootstrapModuleDef] = {
    val rolesModule = new BootstrapModuleDef {
      make[RolesInfo].from(roleInfo)
      make[LogRouter].from(logger.router)
    }

    val autosetModule = roleAutoSetModule
    val configModule = new ConfigModule(config)
    val dumpContext = parameters.globalParameters.flags.exists(p => RoleAppLauncher.dumpContext.matches(p.name))
    val maybeDumpGraphModule = if (dumpContext) {
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
      make[ComponentsLifecycleManager].from[ComponentsLifecycleManagerImpl]
      make[RoleStarter].from[RoleStarterImpl]
      make[Finalizers.CloseablesFinalized].fromResource[Finalizers.CloseablesFinalizer]
      make[Finalizers.ExecutorsFinalized].fromResource[Finalizers.ExecutorsFinalizer]
    }
    Seq(baseMod)
  }

}


