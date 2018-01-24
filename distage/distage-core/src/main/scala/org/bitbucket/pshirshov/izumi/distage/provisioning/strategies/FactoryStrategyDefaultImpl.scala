package org.bitbucket.pshirshov.izumi.distage.provisioning.strategies

import java.lang.reflect.Method

import net.sf.cglib.proxy.{MethodInterceptor, MethodProxy}
import org.bitbucket.pshirshov.izumi.distage.model.plan.ExecutableOp
import org.bitbucket.pshirshov.izumi.distage.model.plan.ExecutableOp.WiringOp
import org.bitbucket.pshirshov.izumi.distage.provisioning.cglib.CglibTools
import org.bitbucket.pshirshov.izumi.distage.provisioning.{OpResult, OperationExecutor, ProvisioningContext}

import scala.reflect.runtime._


class FactoryStrategyDefaultImpl extends FactoryStrategy {
  def makeFactory(context: ProvisioningContext, executor: OperationExecutor, f: WiringOp.InstantiateFactory): Seq[OpResult] = {
    // at this point we definitely have all the dependencies instantiated
    val allRequiredKeys = f.wiring.associations.map(_.wireWith).toSet
    val narrowed = mkExecutor(executor, context.narrow(allRequiredKeys))

    val dispatcher = new MethodInterceptor {
      override def intercept(o: scala.Any, method: Method, objects: Array[AnyRef], methodProxy: MethodProxy): AnyRef = {
        if (false) {
          ???
        } else {
          methodProxy.invokeSuper(o, objects)
        }
      }
    }

    val runtimeClass = currentMirror.runtimeClass(f.wiring.factoryType.tpe)

    CglibTools.dynamic(f, dispatcher, runtimeClass)
  }

  private def mkExecutor(executor: OperationExecutor, newContext: ProvisioningContext) = {
    new OperationExecutor {
      override def execute(context: ProvisioningContext, step: ExecutableOp): Seq[OpResult] = {
        executor.execute(newContext, step)
      }
    }
  }
}

object FactoryStrategyDefaultImpl {
  final val instance = new FactoryStrategyDefaultImpl()
}

