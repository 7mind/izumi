package izumi.distage.bootstrap

import izumi.distage.model.provisioning.proxies.ProxyProvider.ProxyProviderFailingImpl

object CglibBootstrap {
  type CglibProxyProvider = ProxyProviderFailingImpl
}
