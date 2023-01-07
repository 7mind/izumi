package izumi.distage.model.provisioning.proxies

import izumi.distage.model.exceptions.interpretation.ProvisionerIssue
import izumi.distage.model.plan.ExecutableOp
import izumi.distage.model.provisioning.proxies.ProxyProvider.{DeferredInit, ProxyContext}
import izumi.distage.model.reflection.DIKey

trait ProxyProvider {
  def makeCycleProxy(deferredKey: DIKey, proxyContext: ProxyContext): Either[ProvisionerIssue, DeferredInit]
}

object ProxyProvider {
  class ProxyProviderFailingImpl(cause: ProvisionerIssue.ProxyFailureCause) extends ProxyProvider {
    def makeCycleProxy(deferredKey: DIKey, proxyContext: ProxyContext): Either[ProvisionerIssue, DeferredInit] = {
      Left(
        ProvisionerIssue.ProxyProviderFailingImplCalled(
          deferredKey,
          this,
          cause,
        )
      )
    }
  }

  final case class ProxyContext(runtimeClass: Class[?], op: ExecutableOp, params: ProxyParams)

  sealed trait ProxyParams
  object ProxyParams {
    case object Empty extends ProxyParams
    final case class Params(types: Array[Class[?]], values: Array[Any]) extends ProxyParams {
      override def toString: String = s"Params(${types.mkString("[", ",", "]")}, ${values.mkString("[", ",", "]")})"
    }
  }

  final case class DeferredInit(dispatcher: ProxyDispatcher, proxy: AnyRef)
}
