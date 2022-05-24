package izumi.distage.provisioning.strategies

import izumi.distage.model.exceptions.interpretation.UnsupportedOpException
import izumi.distage.model.plan.ExecutableOp.ProxyOp
import izumi.distage.model.provisioning.ProvisioningKeyProvider
import izumi.distage.model.provisioning.proxies.ProxyProvider
import izumi.distage.model.provisioning.proxies.ProxyProvider.DeferredInit
import izumi.distage.model.reflection.MirrorProvider
import izumi.distage.model.reflection.SafeType
import scala.annotation.unused

abstract class ProxyStrategyDefaultImplPlatformSpecific(
  @unused proxyProvider: ProxyProvider,
  @unused mirrorProvider: MirrorProvider,
) {

  protected def makeCogenProxy(@unused context: ProvisioningKeyProvider, tpe: SafeType, op: ProxyOp.MakeProxy): DeferredInit = {
    failCogenProxy(tpe, op)
  }

  protected def failCogenProxy(tpe: SafeType, op: ProxyOp.MakeProxy): Nothing = {
    throw new UnsupportedOpException(s"Cglib proxies are not supported on Scala.js, check documentation & try using by-name parameters! $tpe", op)
  }

}
