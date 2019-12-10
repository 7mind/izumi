package izumi.distage.bootstrap

import izumi.distage.model.definition.BootstrapContextModule

object CglibBootstrap {
  final lazy val cogenBootstrap: BootstrapContextModule = BootstrapLocator.defaultBootstrap// ++ defaultCogen

//  private[this] final lazy val defaultCogen: ModuleBase = new ModuleDef {
//    make[ProxyProvider].from[CglibProxyProvider]
//  }
}
