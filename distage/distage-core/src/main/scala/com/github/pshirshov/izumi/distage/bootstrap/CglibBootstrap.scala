package com.github.pshirshov.izumi.distage.bootstrap

import com.github.pshirshov.izumi.distage.model.definition.{ModuleBase, ModuleDef}
import com.github.pshirshov.izumi.distage.model.provisioning.strategies.ProxyProvider
import com.github.pshirshov.izumi.distage.provisioning.strategies.cglib.CglibProxyProvider

object CglibBootstrap {
  final lazy val cogenBootstrap = DefaultBootstrapContext.defaultBootstrap ++ defaultCogen

  private final lazy val defaultCogen: ModuleBase = new ModuleDef {
    make[ProxyProvider].from[CglibProxyProvider]
  }

}
