package izumi.distage.provisioning.strategies

import izumi.distage.model.exceptions.interpretation.ProvisionerIssue
import izumi.distage.model.plan.ExecutableOp.{ProxyOp, WiringOp}
import izumi.distage.model.plan.Wiring
import izumi.distage.model.provisioning.ProvisioningKeyProvider
import izumi.distage.model.provisioning.proxies.ProxyProvider
import izumi.distage.model.provisioning.proxies.ProxyProvider.{DeferredInit, ProxyContext, ProxyParams}
import izumi.distage.model.reflection.Provider.ProviderType
import izumi.distage.model.reflection.{DIKey, LinkedParameter, MirrorProvider, SafeType}
import izumi.fundamentals.reflection.TypeUtil
import izumi.reflect.TagK

abstract class ProxyStrategyDefaultImplPlatformSpecific(
  proxyProvider: ProxyProvider,
  mirrorProvider: MirrorProvider,
) {

  protected def makeCogenProxy[F[_]: TagK](
    context: ProvisioningKeyProvider,
    tpe: SafeType,
    op: ProxyOp.MakeProxy,
  ): Either[ProvisionerIssue, DeferredInit] = {
    for {
      runtimeClass <- mirrorProvider.runtimeClass(tpe).toRight(ProvisionerIssue.NoRuntimeClass(op.target))
      classConstructorParams <-
        if (noArgsConstructor(tpe)) {
          Right(ProxyParams.Empty)
        } else {
          for {
            allArgsAsNull <- {
              op.op match {
                case WiringOp.CallProvider(_, f: Wiring.SingletonWiring.Function, _) if f.provider.providerType eq ProviderType.Class =>
                  // for class constructors, try to fetch known dependencies from the object graph
                  import izumi.functional.IzEither.*
                  f.associations
                    .map(a => fetchNonforwardRefParamWithClass(context, op.forwardRefs, a))
                    .biAggregate
                    .map(_.toArray)
                    .left
                    .map(
                      missing =>
                        ProvisionerIssue.MissingRef(op.target, "Proxy precondition failed: non-forwarding key expected to be in context but wasn't", missing.toSet)
                    )
                case _ =>
                  // otherwise fill everything with nulls
                  Right(
                    runtimeClass.getConstructors.head.getParameterTypes
                      .map(clazz => clazz -> TypeUtil.defaultValue(clazz))
                  )
              }
            }
          } yield {
            val (argClasses, argValues) = allArgsAsNull.unzip
            ProxyParams.Params(argClasses, argValues)
          }
        }
      proxyContext = ProxyContext(runtimeClass, op, classConstructorParams)
    } yield {
      proxyProvider.makeCycleProxy(op.target, proxyContext)
    }
  }

  protected def failCogenProxy(tpe: SafeType, op: ProxyOp.MakeProxy): Left[ProvisionerIssue, Unit] = {
    Left(ProvisionerIssue.UnsupportedOp(tpe, op, "tried to make proxy of non-proxyable (final?) class"))
  }

  private def fetchNonforwardRefParamWithClass(
    context: ProvisioningKeyProvider,
    forwardRefs: Set[DIKey],
    param: LinkedParameter,
  ): Either[List[DIKey], (Class[?], Any)] = {
    val clazz: Class[?] = if (param.isByName) {
      classOf[Function0[?]]
    } else if (param.wasGeneric) {
      classOf[Any]
    } else {
      param.key.tpe.cls
    }

    val declaredKey = param.key
    // see keep proxies alive in case of intersecting loops
    // there may be a situation when we have intersecting loops resolved independently and real implementation may be not available yet, so fallback is necessary
    val realKey = declaredKey match {
      case DIKey.ProxyInitKey(proxied) =>
        proxied
      case key =>
        key
    }

    param match {
      case param if forwardRefs.contains(realKey) || forwardRefs.contains(declaredKey) =>
        // substitute forward references by `null`
        Right((clazz, TypeUtil.defaultValue(param.key.tpe.cls)))
      case param =>
        context.fetchKey(declaredKey, param.isByName) match {
          case Some(v) =>
            Right((clazz, v.asInstanceOf[Any]))

          case None =>
            Left(List(param.key))
        }
    }
  }

  private def noArgsConstructor(tpe: SafeType): Boolean = {
    val constructors = tpe.cls.getConstructors
    constructors.isEmpty || constructors.exists(_.getParameters.isEmpty)
  }

}
