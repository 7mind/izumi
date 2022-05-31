package izumi.distage.bootstrap

import izumi.distage.model.provisioning.proxies.ProxyProvider
import izumi.fundamentals.reflection.TypeUtil

import scala.util.Try

object DynamicProxyBootstrap {

  val dynProxyProviderName = "izumi.distage.provisioning.strategies.cglib.DynamicProxyProvider$"
  val DynamicProxyProvider: ProxyProvider =
    Try(TypeUtil.instantiateObject[ProxyProvider](Class.forName(dynProxyProviderName))).toOption match {
      case Some(value) =>
        value
      case None =>
        import izumi.distage.model.provisioning.proxies.ProxyProvider.ProxyProviderFailingImpl
        ProxyProviderFailingImpl
    }

}
