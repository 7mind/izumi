package izumi.distage.bootstrap

import izumi.distage.model.definition.BootstrapContextModule

object CglibBootstrap {
  final def cogenBootstrap: BootstrapContextModule = BootstrapLocator.defaultBootstrap
}
