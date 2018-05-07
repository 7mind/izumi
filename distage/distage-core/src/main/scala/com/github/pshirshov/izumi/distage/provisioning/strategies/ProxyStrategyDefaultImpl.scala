package com.github.pshirshov.izumi.distage.provisioning.strategies

import com.github.pshirshov.izumi.distage.model.exceptions.DIException
import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp.CreateSet
import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp.{ProxyOp, WiringOp}
import com.github.pshirshov.izumi.distage.model.provisioning.strategies.ProxyStrategy
import com.github.pshirshov.izumi.distage.model.provisioning.{OpResult, OperationExecutor, ProvisioningContext}
import com.github.pshirshov.izumi.distage.model.reflection.ReflectionProvider
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse
import com.github.pshirshov.izumi.distage.provisioning.cglib.{CglibNullMethodInterceptor, CglibRefDispatcher, CglibTools, ProxyParams}

// CGLIB-CLASSLOADER: when we work under sbt cglib fails to instantiate set
trait FakeSet[A] extends Set[A]

/**
  * Limitations:
  * - Will not work for any class which performs any operations on forwarding refs within constructor
  * - Untested on constructors accepting primitive values, will fail most likely
  */
class ProxyStrategyDefaultImpl(reflectionProvider: ReflectionProvider.Runtime) extends ProxyStrategy {
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
      case op: CreateSet =>
        // CGLIB-CLASSLOADER: when we work under sbt cglib fails to instantiate set
        RuntimeDIUniverse.SafeType.get[FakeSet[_]]
        //op.target.symbol
      case op =>
        throw new DIException(s"Operation unsupported by proxy mechanism: $op", null)
    }

    val constructors = tpe.tpe.decls.filter(_.isConstructor)
    val hasTrivial = constructors.exists(_.asMethod.paramLists.forall(_.isEmpty))
    val runtimeClass = RuntimeDIUniverse.mirror.runtimeClass(tpe.tpe)

    val params = if (constructors.isEmpty || hasTrivial) {
      ProxyParams.Empty
    } else {
      val params = reflectionProvider.constructorParameters(makeProxy.op.target.symbol)

      val args = params.map {
        param =>
          val value = param match {
            case p if makeProxy.forwardRefs.contains(p.wireWith) =>
              null

            case p =>
              context.fetchKey(p.wireWith).orNull.asInstanceOf[AnyRef]
          }

          RuntimeDIUniverse.mirror.runtimeClass(param.wireWith.symbol.tpe) -> value
      }

      ProxyParams.Params(args.map(_._1).toArray, args.map(_._2).toArray)
    }

    val nullDispatcher = new CglibNullMethodInterceptor(makeProxy.target)

    val nullProxy = CglibTools.mkDynamic(nullDispatcher, runtimeClass, makeProxy, params) {
      proxyInstance =>
        proxyInstance
    }

    val dispatcher = new CglibRefDispatcher(nullProxy)

    CglibTools.mkDynamic(dispatcher, runtimeClass, makeProxy, params) {
      proxyInstance =>
        Seq(
          OpResult.NewInstance(makeProxy.target, proxyInstance)
          , OpResult.NewInstance(proxyKey(makeProxy.target), dispatcher)
        )
    }
  }

  private def proxyKey(m: RuntimeDIUniverse.DIKey) = {
    RuntimeDIUniverse.DIKey.ProxyElementKey(m, RuntimeDIUniverse.SafeType.get[CglibRefDispatcher])
  }

}

