package izumi.distage.provisioning.strategies

import izumi.distage.model.exceptions.{MissingRefException, NoRuntimeClassException, UnsupportedOpException}
import izumi.distage.model.plan.ExecutableOp.{ProxyOp, WiringOp}
import izumi.distage.model.plan.Wiring
import izumi.distage.model.provisioning.ProvisioningKeyProvider
import izumi.distage.model.provisioning.proxies.ProxyProvider
import izumi.distage.model.provisioning.proxies.ProxyProvider.{DeferredInit, ProxyContext, ProxyParams}
import izumi.distage.model.reflection.Provider.ProviderType
import izumi.distage.model.reflection.{AssociationP, DIKey, MirrorProvider, SafeType}
import izumi.fundamentals.reflection.TypeUtil

abstract class ProxyStrategyDefaultImplPlatformSpecific(
  proxyProvider: ProxyProvider,
  mirrorProvider: MirrorProvider,
) {

  protected def makeCogenProxy(context: ProvisioningKeyProvider, tpe: SafeType, op: ProxyOp.MakeProxy): DeferredInit = {
    val runtimeClass = mirrorProvider.runtimeClass(tpe).getOrElse(throw new NoRuntimeClassException(op.target))

    val classConstructorParams = if (noArgsConstructor(tpe)) {
      ProxyParams.Empty
    } else {
      val allArgsAsNull: Array[(Class[_], Any)] = {
        op.op match {
          case WiringOp.CallProvider(_, f: Wiring.SingletonWiring.Function, _) if f.provider.providerType eq ProviderType.Class =>
            // for class constructors, try to fetch known dependencies from the object graph
            f.associations.map(a => fetchNonforwardRefParamWithClass(context, op.forwardRefs, a.asInstanceOf)).toArray
          case _ =>
            // otherwise fill everything with nulls
            runtimeClass
              .getConstructors.head.getParameterTypes
              .map(clazz => clazz -> TypeUtil.defaultValue(clazz))
        }
      }
      val (argClasses, argValues) = allArgsAsNull.unzip
      ProxyParams.Params(argClasses, argValues)
    }

    val proxyContext = ProxyContext(runtimeClass, op, classConstructorParams)

    proxyProvider.makeCycleProxy(op.target, proxyContext)
  }

  protected def failCogenProxy(tpe: SafeType, op: ProxyOp.MakeProxy): Nothing = {
    throw new UnsupportedOpException(s"Tried to make proxy of non-proxyable (final?) $tpe", op)
  }

  private def fetchNonforwardRefParamWithClass(context: ProvisioningKeyProvider, forwardRefs: Set[DIKey], param: AssociationP.Parameter): (Class[_], Any) = {
    val clazz: Class[_] = if (param.isByName) {
      classOf[Function0[_]]
    } else if (param.wasGeneric) {
      classOf[Any]
    } else {
      param.key.tpe.cls
    }

    val value = param match {
      case param if forwardRefs.contains(param.key) =>
        // substitute forward references by `null`
        TypeUtil.defaultValue(param.key.tpe.cls)
      case param =>
        context.fetchKey(param.key, param.isByName) match {
          case Some(v) =>
            v.asInstanceOf[Any]
          case None =>
            throw new MissingRefException(s"Proxy precondition failed: non-forwarding key expected to be in context but wasn't: ${param.key}", Set(param.key), None)
        }
    }

    (clazz, value)
  }

  private def noArgsConstructor(tpe: SafeType): Boolean = {
    val constructors = tpe.cls.getConstructors
    constructors.isEmpty || constructors.exists(_.getParameters.isEmpty)
  }

}
