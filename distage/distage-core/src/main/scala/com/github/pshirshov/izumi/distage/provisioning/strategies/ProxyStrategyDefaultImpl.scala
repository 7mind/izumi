package com.github.pshirshov.izumi.distage.provisioning.strategies

import com.github.pshirshov.izumi.distage.model.exceptions._
import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp.{CreateSet, ProxyOp, WiringOp}
import com.github.pshirshov.izumi.distage.model.provisioning.strategies._
import com.github.pshirshov.izumi.distage.model.provisioning.{OpResult, OperationExecutor, ProvisioningKeyProvider}
import com.github.pshirshov.izumi.distage.model.reflection.ReflectionProvider
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse._

// CGLIB-CLASSLOADER: when we work under sbt cglib fails to instantiate set
trait FakeSet[A] extends Set[A]

/**
  * Limitations:
  * - Will not work for any class which performs any operations on forwarding refs within constructor
  * - Untested on constructors accepting primitive values, will fail most likely
  */
class ProxyStrategyDefaultImpl(reflectionProvider: ReflectionProvider.Runtime, proxyProvider: ProxyProvider) extends ProxyStrategy {
  def initProxy(context: ProvisioningKeyProvider, executor: OperationExecutor, initProxy: ProxyOp.InitProxy): Seq[OpResult] = {
    val key = proxyKey(initProxy.target)
    context.fetchKey(key) match {
      case Some(adapter: ProxyDispatcher) =>
        executor.execute(context, initProxy.proxy.op).head match {
          case OpResult.NewInstance(_, instance) =>
            adapter.init(instance.asInstanceOf[AnyRef])
          case r =>
            throw new UnexpectedProvisionResultException(s"Unexpected operation result for $key: $r", Seq(r))
        }

      case _ =>
        throw new MissingProxyAdapterException(s"Cannot get adapter $key for $initProxy", key, initProxy)
    }

    Seq()
  }

  def makeProxy(context: ProvisioningKeyProvider, makeProxy: ProxyOp.MakeProxy): Seq[OpResult] = {
    val tpe = proxyTargetType(makeProxy)

    val runtimeClass = mirror.runtimeClass(tpe.tpe)

    val params = if (hasNoDeps(tpe)) {
      // It's very strange if it happens that we need to create a proxy for a class without dependencies
      ProxyParams.Empty
    } else {
      val params = reflectionProvider.constructorParameters(makeProxy.op.target.tpe)

      val args = params.map {
        param =>
          val value = param match {
            case p if makeProxy.forwardRefs.contains(p.wireWith) =>
              null

            case p =>
              context.fetchKey(p.wireWith).orNull.asInstanceOf[AnyRef]
          }

          if (param.isByName) {
            import u._
            mirror.runtimeClass(typeOf[() => Any]) -> (() => value)
          } else {
            mirror.runtimeClass(param.wireWith.tpe.tpe) -> value
          }
      }

      ProxyParams.Params(args.map(_._1).toArray, args.map(_._2).toArray)
    }

    val proxyContext = ProxyContext(runtimeClass, makeProxy, params)

    val proxyInstance = proxyProvider.makeCycleProxy(CycleContext(makeProxy.target), proxyContext)

    Seq(
      OpResult.NewInstance(makeProxy.target, proxyInstance.proxy)
      , OpResult.NewInstance(proxyKey(makeProxy.target), proxyInstance.dispatcher)
    )
  }

  private def hasNoDeps(tpe: RuntimeDIUniverse.SafeType): Boolean = {
    val constructors = tpe.tpe.decls.filter(_.isConstructor)
    val hasTrivial = constructors.exists(_.asMethod.paramLists.forall(_.isEmpty))
    val hasNoDependencies = constructors.isEmpty || hasTrivial
    hasNoDependencies
  }

  protected def proxyTargetType(makeProxy: ProxyOp.MakeProxy): SafeType = {
    makeProxy.op match {
      case op: WiringOp.InstantiateTrait =>
        op.wiring.instanceType
      case op: WiringOp.InstantiateClass =>
        op.wiring.instanceType
      case op: WiringOp.InstantiateFactory =>
        op.wiring.factoryType
      case op: WiringOp.CallProvider =>
        op.wiring.instanceType
      case op: WiringOp.CallFactoryProvider =>
        op.wiring.provider.ret
      case _: CreateSet =>
        // CGLIB-CLASSLOADER: when we work under sbt cglib fails to instantiate set
        SafeType.get[FakeSet[_]]
      //op.target.symbol
      case op: WiringOp.ReferenceInstance =>
        throw new UnsupportedOpException(s"Tried to execute nonsensical operation - shouldn't create proxies for references: $op", op)
      case op: WiringOp.ReferenceKey =>
        throw new UnsupportedOpException(s"Tried to execute nonsensical operation - shouldn't create proxies for references: $op", op)
      case op: ProxyOp.MakeProxy =>
        throw new UnsupportedOpException(s"Tried to execute nonsensical operation - can't make a proxy for proxy!: $op", op)
    }
  }

  protected def proxyKey(m: DIKey): DIKey = {
    DIKey.ProxyElementKey(m, SafeType.get[ProxyDispatcher])
  }

}

