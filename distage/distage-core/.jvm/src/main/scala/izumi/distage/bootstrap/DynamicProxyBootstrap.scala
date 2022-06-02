package izumi.distage.bootstrap

import izumi.distage.model.provisioning.proxies.ProxyProvider
import izumi.distage.model.provisioning.proxies.ProxyProvider.ProxyProviderFailingImpl
import izumi.fundamentals.platform.build.MacroParameters
import izumi.fundamentals.reflection.TypeUtil

import scala.util.Try

object DynamicProxyBootstrap {
  val dynProxyProviderName = "izumi.distage.provisioning.strategies.dynamicproxy.DynamicProxyProvider$"

  val DynamicProxyProvider: ProxyProvider =
    Try(TypeUtil.instantiateObject[ProxyProvider](Class.forName(dynProxyProviderName))).toOption match {
      case Some(value) =>
        value
      case None =>
        new ProxyProviderFailingImpl(
          proxyContext =>
            s"""DynamicProxyProvider: couldn't create a cycle-breaking proxy - cyclic dependencies support is enabled, but proxy provider class is not on the classpath, couldn't instantiate `$dynProxyProviderName`.
               |
               |Please add dependency on `libraryDependencies += "io.7mind.izumi" %% "distage-core-proxy-bytebuddy" % "${MacroParameters
                .artifactVersion()}"` to your build.
               |
               |failed op: ${proxyContext.op}""".stripMargin
        )
    }

}
