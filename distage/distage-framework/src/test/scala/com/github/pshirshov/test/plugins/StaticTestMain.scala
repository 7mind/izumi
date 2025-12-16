package com.github.pshirshov.test.plugins

import distage.{ClassConstructor, ModuleDef, TagK}
import izumi.distage.model.definition.{Module, ModuleBase}
import izumi.distage.modules.DefaultModule2
import izumi.distage.plugins.{PluginConfig, PluginDef}
import izumi.distage.roles.RoleAppMain
import izumi.distage.roles.RoleAppMain.ArgV
import izumi.distage.roles.model.definition.RoleModuleDef
import izumi.functional.quasi.QuasiApplicative
import izumi.fundamentals.platform.IzPlatform
import izumi.fundamentals.platform.functional.Identity
import izumi.reflect.TagKK
import logstage.LogIO2

object StaticTestMain extends RoleAppMain.Launcher1[cats.effect.IO] {
  override protected def pluginConfig: PluginConfig = {
    (if (IzPlatform.isScalaJS) {
       PluginConfig.compileTime("com.github.pshirshov.test.plugins")
     } else {
       PluginConfig.cached("com.github.pshirshov.test.plugins")
     }) ++ StaticTestMain.staticTestMainPlugin[cats.effect.IO, Identity]
  }

  private[plugins] def staticTestMainPlugin[F[_]: TagK, G[_]: TagK]: ModuleBase = new PluginDef with RoleModuleDef {
    makeRole[StaticTestRole[F]].fromEffect {
      ClassConstructor[StaticTestRole[F]]
        .flatAp((G: QuasiApplicative[G]) => G.pure(_: StaticTestRole[F]))
    }
    makeRole[DependingRole[F]]
  }
}

object StaticTestMainBadEffect extends RoleAppMain.LauncherIdentity {
  override protected def pluginConfig: PluginConfig = {
    (if (IzPlatform.isScalaJS) {
       PluginConfig.compileTime("com.github.pshirshov.test.plugins")
     } else {
       PluginConfig.cached("com.github.pshirshov.test.plugins")
     }) ++ StaticTestMain.staticTestMainPlugin[Identity, cats.effect.IO]
  }
}

class StaticTestMainLogIO2[F[+_, +_]: TagKK: DefaultModule2] extends RoleAppMain.LauncherBIO[F] {

  override protected def roleAppBootOverrides(argv: ArgV): Module = super.roleAppBootOverrides(argv) ++ new ModuleDef {
    make[Boolean].named("distage.roles.always-include-reference-role-configs").fromValue(true)
  }

  override protected def pluginConfig: PluginConfig = {
    (if (IzPlatform.isScalaJS) {
       PluginConfig.compileTime("com.github.pshirshov.test.plugins")
     } else {
       PluginConfig.cached("com.github.pshirshov.test.plugins")
     }) ++ StaticTestMain.staticTestMainPlugin[F[Throwable, _], F[Throwable, _]] ++ new PluginDef {
      modify[StaticTestRole[F[Throwable, _]]]
        .addDependency[LogIO2[F]]
    }
  }
}
