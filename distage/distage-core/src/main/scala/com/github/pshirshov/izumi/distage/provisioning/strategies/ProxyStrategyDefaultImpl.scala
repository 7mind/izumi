package com.github.pshirshov.izumi.distage.provisioning.strategies

import com.github.pshirshov.izumi.distage.model.exceptions.DIException
import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp.{ProxyOp, WiringOp}
import com.github.pshirshov.izumi.distage.model.provisioning.strategies.ProxyStrategy
import com.github.pshirshov.izumi.distage.model.provisioning.{OpResult, OperationExecutor, ProvisioningContext}
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeUniverse
import com.github.pshirshov.izumi.distage.provisioning.cglib.{CglibNullMethodInterceptor, CglibRefDispatcher, CglibTools}

class ProxyStrategyDefaultImpl extends ProxyStrategy {
  def initProxy(context: ProvisioningContext, executor: OperationExecutor, initProxy: ProxyOp.InitProxy): Seq[OpResult] = {
    val key = proxyKey(initProxy.target)
    context.fetchKey(key) match {
      case Some(adapter: CglibRefDispatcher) =>
        executor.execute(context, initProxy.proxy.op).head match {
          case OpResult.NewInstance(_, instance) =>
            adapter.reference.set(instance.asInstanceOf[AnyRef])
          case r =>
            throw new DIException(s"Unexpected operation result for $key: $r", null)
        }

      case _ =>
        throw new DIException(s"Cannot get adapter $key for $initProxy", null)
    }

    Seq()
  }

  def makeProxy(context: ProvisioningContext, makeProxy: ProxyOp.MakeProxy): Seq[OpResult] = {
    val tpe = makeProxy.op match {
      case op: WiringOp.InstantiateTrait =>
        op.wiring.instanceType
      case op: WiringOp.InstantiateClass =>
        op.wiring.instanceType
      case op: WiringOp.InstantiateFactory =>
        op.wiring.factoryType
      case op =>
        throw new DIException(s"Operation unsupported by proxy mechanism: $op", null)
    }

    val constructors = tpe.tpe.decls.filter(_.isConstructor)
    val constructable = constructors.forall(_.asMethod.paramLists.forall(_.isEmpty))
    if (!constructable) {
      throw new DIException(s"Failed to instantiate proxy ${makeProxy.target}. All the proxy constructors must be zero-arg though we have $constructors", null)
    }

    val runtimeClass = RuntimeUniverse.mirror.runtimeClass(tpe.tpe)
    val nullDispatcher = new CglibNullMethodInterceptor(makeProxy.target)
    val nullProxy = CglibTools.mkDynamic(nullDispatcher, runtimeClass, makeProxy) {
      proxyInstance =>
        proxyInstance
    }

    val dispatcher = new CglibRefDispatcher(nullProxy)

    CglibTools.mkDynamic(dispatcher, runtimeClass, makeProxy) {
      proxyInstance =>
        Seq(
          OpResult.NewInstance(makeProxy.target, proxyInstance)
          , OpResult.NewInstance(proxyKey(makeProxy.target), dispatcher)
        )
    }
  }

  private def proxyKey(m: RuntimeUniverse.DIKey) = {
    RuntimeUniverse.DIKey.ProxyElementKey(m, RuntimeUniverse.SafeType.get[CglibRefDispatcher])
  }

}

