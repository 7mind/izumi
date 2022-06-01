package izumi.distage.bootstrap

import izumi.distage.model.provisioning.proxies.ProxyProvider
import izumi.distage.model.provisioning.proxies.ProxyProvider.ProxyProviderFailingImpl

object DynamicProxyBootstrap {
  val DynamicProxyProvider: ProxyProvider = ProxyProviderFailingImpl
}
