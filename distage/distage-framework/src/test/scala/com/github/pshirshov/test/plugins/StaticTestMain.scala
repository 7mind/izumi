package com.github.pshirshov.test.plugins

import distage.{ClassConstructor, ModuleDef, TagKK}
import izumi.distage.model.definition.{Module, ModuleBase}
import izumi.distage.modules.DefaultModule
import izumi.distage.plugins.{PluginConfig, PluginDef}
import izumi.distage.roles.RoleAppMain
import izumi.distage.roles.RoleAppMain.ArgV
import izumi.distage.roles.model.definition.RoleModuleDef
import izumi.functional.bio.{Applicative2, Bifunctorized}
import izumi.fundamentals.platform.IzPlatform
import logstage.LogIO2

object StaticTestMain extends RoleAppMain.Launcher1[cats.effect.IO] {
  override protected def pluginConfig: PluginConfig = {
    (if (IzPlatform.isScalaJS) {
       PluginConfig.compileTime("com.github.pshirshov.test.plugins")
     } else {
       PluginConfig.cached("com.github.pshirshov.test.plugins")
     }) ++ StaticTestMain.staticTestMainPlugin[Bifunctorized[cats.effect.IO, +_, +_], Bifunctorized.IdentityBifunctorized]
  }

  private[plugins] def staticTestMainPlugin[F[+_, +_]: TagKK, G[+_, +_]: TagKK]: ModuleBase = new PluginDef with RoleModuleDef {
    makeRole[StaticTestRole[F]].fromEffect {
      ClassConstructor[StaticTestRole[F]]
        .flatAp((G: Applicative2[G]) => G.pure(_: StaticTestRole[F]))
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
     }) ++ StaticTestMain.staticTestMainPlugin[Bifunctorized.IdentityBifunctorized, Bifunctorized[cats.effect.IO, +_, +_]]
  }
}

class StaticTestMainLogIO2[F[+_, +_]: TagKK: DefaultModule] extends RoleAppMain.LauncherBIO[F] {

  override protected def roleAppBootOverrides(argv: ArgV): Module = super.roleAppBootOverrides(argv) ++ new ModuleDef {
    make[Boolean].named("distage.roles.always-include-reference-role-configs").fromValue(true)
  }

  override protected def pluginConfig: PluginConfig = {
    (if (IzPlatform.isScalaJS) {
       PluginConfig.compileTime("com.github.pshirshov.test.plugins")
     } else {
       PluginConfig.cached("com.github.pshirshov.test.plugins")
     }) ++ StaticTestMain.staticTestMainPlugin[F, F] ++ new PluginDef {
      modify[StaticTestRole[F]]
        .addDependency[LogIO2[F]]
    }
  }
}
