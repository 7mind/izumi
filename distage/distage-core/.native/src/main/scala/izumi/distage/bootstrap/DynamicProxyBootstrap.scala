package izumi.distage.bootstrap

import izumi.distage.model.definition.errors.ProvisionerIssue.ProxyFailureCause
import izumi.distage.model.provisioning.proxies.ProxyProvider
import izumi.distage.model.provisioning.proxies.ProxyProvider.ProxyProviderFailingImpl

object DynamicProxyBootstrap {
  val DynamicProxyProvider: ProxyProvider = new ProxyProviderFailingImpl(ProxyFailureCause.ProxiesDisabled())
}
