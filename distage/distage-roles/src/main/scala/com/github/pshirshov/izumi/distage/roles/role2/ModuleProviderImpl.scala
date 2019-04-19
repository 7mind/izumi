package com.github.pshirshov.izumi.distage.roles.role2

import java.util.concurrent.ExecutorService

import com.github.pshirshov.izumi.distage.config.ConfigModule
import com.github.pshirshov.izumi.distage.config.model.AppConfig
import com.github.pshirshov.izumi.distage.model.definition.{BootstrapModuleDef, Module, ModuleDef}
import com.github.pshirshov.izumi.distage.planning.AutoSetModule
import com.github.pshirshov.izumi.distage.planning.extensions.GraphDumpBootstrapModule
import com.github.pshirshov.izumi.distage.roles._
import com.github.pshirshov.izumi.distage.roles.launcher.{ComponentsLifecycleManagerImpl, RoleStarterImpl}
import com.github.pshirshov.izumi.distage.roles.role2.parser.CLIParser
import com.github.pshirshov.izumi.fundamentals.platform.functional.Identity
import com.github.pshirshov.izumi.logstage.api.IzLogger
import com.github.pshirshov.izumi.logstage.api.Log.CustomContext
import com.github.pshirshov.izumi.logstage.api.logger.LogRouter

class ModuleProviderImpl(logger: IzLogger, parameters: CLIParser.RoleAppArguments, config: AppConfig, roleInfo: RolesInfo) extends ModuleProvider {
  private final val roleAutoSetModule = AutoSetModule()
    .register[RoleService]
    .register[AutoCloseable]
    .register[RoleComponent]
    .register[IntegrationCheck]
    .register[ExecutorService]

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
      make[CustomContext].from(CustomContext.empty)
      make[IzLogger]
      make[ComponentsLifecycleManager].from[ComponentsLifecycleManagerImpl]
      make[RoleStarter].from[RoleStarterImpl]
    }
    Seq(baseMod)
  }

}
