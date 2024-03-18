package izumi.distage.bootstrap

import izumi.distage.model.definition.errors.ProvisionerIssue.ProxyFailureCause.CantFindStrategyClass
import izumi.distage.model.provisioning.proxies.ProxyProvider
import izumi.distage.model.provisioning.proxies.ProxyProvider.ProxyProviderFailingImpl
import izumi.fundamentals.reflection.TypeUtil

import scala.util.Try

object DynamicProxyBootstrap {
  val dynProxyProviderName = "izumi.distage.provisioning.strategies.dynamicproxy.DynamicProxyProvider$"

  val DynamicProxyProvider: ProxyProvider =
    Try(TypeUtil.instantiateObject[ProxyProvider](Class.forName(dynProxyProviderName))).toOption match {
      case Some(value) =>
        value
      case None =>
        new ProxyProviderFailingImpl(CantFindStrategyClass(dynProxyProviderName))
    }

}
