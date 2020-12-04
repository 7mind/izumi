package com.github.pshirshov.test.plugins

import com.github.pshirshov.test.plugins.StaticTestMain.staticTestMainPlugin
import distage.{ClassConstructor, TagK}
import izumi.distage.model.effect.QuasiApplicative
import izumi.distage.plugins.{PluginConfig, PluginDef}
import izumi.distage.roles.RoleAppMain
import izumi.distage.roles.model.definition.RoleModuleDef

object StaticTestMain extends RoleAppMain.LauncherCats[cats.effect.IO] {
  override protected def pluginConfig: PluginConfig = PluginConfig.cached("com.github.pshirshov.test.plugins") ++ staticTestMainPlugin[cats.effect.IO]

  private[plugins] def staticTestMainPlugin[F[_]: TagK] = new PluginDef with RoleModuleDef {
    makeRole[StaticTestRole[F]].fromEffect {
      ClassConstructor[StaticTestRole[F]]
        .flatAp((F: QuasiApplicative[F]) => F.pure(_: StaticTestRole[F]))
    }
    makeRole[DependingRole]
  }
}

object StaticTestMainBadEffect extends RoleAppMain.LauncherIdentity {
  override protected def pluginConfig: PluginConfig = PluginConfig.cached("com.github.pshirshov.test.plugins") ++ staticTestMainPlugin[cats.effect.IO]
}
