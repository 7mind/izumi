package izumi.distage.provisioning.strategies

import izumi.distage.model.definition.errors.ProvisionerIssue
import izumi.distage.model.plan.ExecutableOp.ProxyOp
import izumi.distage.model.provisioning.ProvisioningKeyProvider
import izumi.distage.model.provisioning.proxies.ProxyProvider
import izumi.distage.model.provisioning.proxies.ProxyProvider.DeferredInit
import izumi.distage.model.reflection.MirrorProvider
import izumi.distage.model.reflection.SafeType
import izumi.fundamentals.platform.language.Quirks

import scala.annotation.unused

abstract class ProxyStrategyDefaultImplPlatformSpecific(
  @unused proxyProvider: ProxyProvider,
  @unused mirrorProvider: MirrorProvider,
) {

  protected def makeCogenProxy(
    context: ProvisioningKeyProvider,
    tpe: SafeType,
    op: ProxyOp.MakeProxy,
  ): Either[ProvisionerIssue, DeferredInit] = {
    Quirks.discard(context)
    failCogenProxy(tpe, op)
  }

  protected def failCogenProxy(tpe: SafeType, op: ProxyOp.MakeProxy): Left[ProvisionerIssue, Nothing] = {
    Left(ProvisionerIssue.UnsupportedOp(tpe, op, "cglib proxies are not supported on Scala.js, check documentation & try using by-name parameters!"))

  }
}
