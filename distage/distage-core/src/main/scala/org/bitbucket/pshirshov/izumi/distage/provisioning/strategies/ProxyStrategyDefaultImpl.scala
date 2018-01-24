package org.bitbucket.pshirshov.izumi.distage.provisioning.strategies

import org.bitbucket.pshirshov.izumi.distage.model.exceptions.DIException
import org.bitbucket.pshirshov.izumi.distage.model.plan.ExecutableOp.{ProxyOp, WiringOp}
import org.bitbucket.pshirshov.izumi.distage.model.{DIKey, EqualitySafeType}
import org.bitbucket.pshirshov.izumi.distage.provisioning.cglib.{CglibNullMethodInterceptor, CglibRefDispatcher, CglibTools}
import org.bitbucket.pshirshov.izumi.distage.provisioning.{OpResult, OperationExecutor, ProvisioningContext}

import scala.reflect.runtime.currentMirror

class ProxyStrategyDefaultImpl extends ProxyStrategy {
  def initProxy(context: ProvisioningContext, executor: OperationExecutor, i: ProxyOp.InitProxy): Seq[OpResult] = {
    val key = proxyKey(i.target)
    context.fetchKey(key) match {
      case Some(adapter: CglibRefDispatcher) =>
        executor.execute(context, i.proxy.op).head match {
          case OpResult.NewInstance(_, instance) =>
            adapter.reference.set(instance.asInstanceOf[AnyRef])
          case r =>
            throw new DIException(s"Unexpected operation result for $key: $r", null)
        }

      case _ =>
        throw new DIException(s"Cannot get adapter $key for $i", null)
    }

    Seq()
  }

  def makeProxy(context: ProvisioningContext, m: ProxyOp.MakeProxy): Seq[OpResult] = {
    val tpe = m.op match {
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
      throw new DIException(s"Failed to instantiate proxy ${m.target}. All the proxy constructors must be zero-arg though we have $constructors", null)
    }

    val runtimeClass = currentMirror.runtimeClass(tpe.tpe)
    val nullDispatcher = new CglibNullMethodInterceptor(m.target)
    val nullProxy = CglibTools.mkdynamic(nullDispatcher, tpe, runtimeClass, m) {
      proxyInstance =>
        proxyInstance
    }

    val dispatcher = new CglibRefDispatcher(nullProxy)

    CglibTools.mkdynamic(dispatcher, tpe, runtimeClass, m) {
      proxyInstance =>
        Seq(
          OpResult.NewInstance(m.target, proxyInstance)
          , OpResult.NewInstance(proxyKey(m.target), dispatcher)
        )
    }
  }

  private def proxyKey(m: DIKey) = {
    DIKey.ProxyElementKey(m, EqualitySafeType.get[CglibRefDispatcher])
  }

}

object ProxyStrategyDefaultImpl {
  final val instance = new ProxyStrategyDefaultImpl()
}
