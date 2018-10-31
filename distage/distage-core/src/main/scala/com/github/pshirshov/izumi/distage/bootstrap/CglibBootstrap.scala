package com.github.pshirshov.izumi.distage.bootstrap

import com.github.pshirshov.izumi.distage.model.definition.{BootstrapContextModule, ModuleBase, ModuleDef}
import com.github.pshirshov.izumi.distage.model.provisioning.strategies.ProxyProvider
import com.github.pshirshov.izumi.distage.provisioning.strategies.cglib.CglibProxyProvider

object CglibBootstrap {
  final lazy val cogenBootstrap: BootstrapContextModule = DefaultBootstrapContext.defaultBootstrap ++ defaultCogen

  private final lazy val defaultCogen: ModuleBase = new ModuleDef {
    make[ProxyProvider].from[CglibProxyProvider]
  }
}
