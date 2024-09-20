package com.github.pshirshov.configapp

import distage.{ModuleDef, Repo}
import distage.config.ConfigModuleDef
import izumi.reflect.TagKK

object abc extends ModuleDef {
  def module[F[+_, +_]: TagKK] = new ConfigModuleDef {
    tag(Repo.Prod)

    makeConfig[MapCaseClass]("x")

    many[TestService2[F]]
      .weak[TestService2[F]]
      .weak[TestService2[F]]
      .weak[TestService2[F]]
      .weak[TestService2[F]]
      .weak[TestService2[F]]

    make[TestService2[F]]
    make[TestService1[F]].from[TImpl[F]]
  }
}
