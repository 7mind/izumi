package com.github.pshirshov.izumi.distage.bootstrap

import com.github.pshirshov.izumi.distage.model.definition.{ModuleBase, ModuleDef}
import com.github.pshirshov.izumi.distage.model.provisioning.strategies.{FactoryStrategy, ProxyProvider, ProxyStrategy, TraitStrategy}
import com.github.pshirshov.izumi.distage.provisioning.strategies.cglib.CglibProxyProvider
import com.github.pshirshov.izumi.distage.provisioning.strategies.{FactoryStrategyDefaultImpl, ProxyStrategyDefaultImpl, TraitStrategyDefaultImpl}

object CglibBootstrap {
  final lazy val cogenBootstrap = DefaultBootstrapContext.defaultBootstrap ++ defaultCogen

  private final lazy val defaultCogen: ModuleBase = new ModuleDef {
    make[ProxyProvider].from[CglibProxyProvider.type]
    make[ProxyStrategy].from[ProxyStrategyDefaultImpl]
    make[FactoryStrategy].from[FactoryStrategyDefaultImpl]
    make[TraitStrategy].from[TraitStrategyDefaultImpl]
  }

}
