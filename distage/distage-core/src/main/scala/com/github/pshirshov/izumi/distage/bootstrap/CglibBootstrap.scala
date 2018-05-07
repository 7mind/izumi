package com.github.pshirshov.izumi.distage.bootstrap

import com.github.pshirshov.izumi.distage.model.definition.{ModuleDef, TrivialModuleDef}
import com.github.pshirshov.izumi.distage.model.provisioning.strategies.{FactoryStrategy, ProxyProvider, ProxyStrategy, TraitStrategy}
import com.github.pshirshov.izumi.distage.provisioning.strategies.cglib.CglibProxyProvider
import com.github.pshirshov.izumi.distage.provisioning.strategies.{FactoryStrategyDefaultImpl, ProxyStrategyDefaultImpl, TraitStrategyDefaultImpl}

object CglibBootstrap {
  final lazy val cogenBootstrap = DefaultBootstrapContext.defaultBootstrap ++ defaultCogen

  private final lazy val defaultCogen: ModuleDef = TrivialModuleDef
    .bind[ProxyProvider].as[CglibProxyProvider.type]
    .bind[ProxyStrategy].as[ProxyStrategyDefaultImpl]
    .bind[FactoryStrategy].as[FactoryStrategyDefaultImpl]
    .bind[TraitStrategy].as[TraitStrategyDefaultImpl]

}
