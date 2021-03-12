package com.github.pshirshov.test.plugins

import com.github.pshirshov.test.plugins.StaticTestMain.staticTestMainPlugin
import distage.{ClassConstructor, TagK}
import izumi.distage.model.effect.QuasiApplicative
import izumi.distage.modules.DefaultModule2
import izumi.distage.plugins.{PluginConfig, PluginDef}
import izumi.distage.roles.RoleAppMain
import izumi.distage.roles.model.definition.RoleModuleDef
import izumi.functional.bio.Async2
import izumi.reflect.TagKK
import logstage.LogIO2

object StaticTestMain extends RoleAppMain.LauncherCats[cats.effect.IO] {
  override protected def pluginConfig: PluginConfig = PluginConfig.cached("com.github.pshirshov.test.plugins") ++ staticTestMainPlugin[cats.effect.IO]

  private[plugins] def staticTestMainPlugin[F[_]: TagK] = new PluginDef with RoleModuleDef {
    makeRole[StaticTestRole[F]].fromEffect {
      ClassConstructor[StaticTestRole[F]]
        .flatAp((F: QuasiApplicative[F]) => F.pure(_: StaticTestRole[F]))
    }
    makeRole[DependingRole[F]]
  }
}

object StaticTestMainBadEffect extends RoleAppMain.LauncherIdentity {
  override protected def pluginConfig: PluginConfig = PluginConfig.cached("com.github.pshirshov.test.plugins") ++ staticTestMainPlugin[cats.effect.IO]
}

class StaticTestMainLogIO2[F[+_, +_]: TagKK: Async2: DefaultModule2] extends RoleAppMain.LauncherBIO2[F] {
  override protected def pluginConfig: PluginConfig = PluginConfig.cached("com.github.pshirshov.test.plugins") ++ staticTestMainPlugin[F[Throwable, ?]] ++ new PluginDef {
    modify[StaticTestRole[F[Throwable, ?]]]
      .addDependency[LogIO2[F]]
  }
}
