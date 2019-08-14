package izumi.distage.provisioning.strategies.cglib

import java.lang.reflect.Method

import izumi.distage.model.plan.ExecutableOp
import izumi.distage.model.plan.ExecutableOp.WiringOp
import izumi.distage.model.provisioning.strategies.TraitIndex
import izumi.distage.model.provisioning.{ProvisioningKeyProvider, WiringExecutor}
import izumi.distage.model.reflection.universe.RuntimeDIUniverse.Wiring._
import izumi.distage.model.reflection.universe.{MirrorProvider, RuntimeDIUniverse}
import izumi.distage.provisioning.FactoryTools
import izumi.distage.provisioning.strategies.cglib.CgLibFactoryMethodInterceptor.JustExecutor
import izumi.distage.provisioning.strategies.cglib.exceptions.CgLibCallException
import izumi.fundamentals.reflection.TypeUtil
import net.sf.cglib.proxy.MethodProxy

protected[distage] class CgLibFactoryMethodInterceptor
(
  factoryMethodIndex: Map[Method, RuntimeDIUniverse.Wiring.Factory.FactoryMethod]
, dependencyMethodIndex: TraitIndex
, narrowedContext: ProvisioningKeyProvider
, executor: WiringExecutor
, op: WiringOp.InstantiateFactory
, mirror: MirrorProvider
) extends CgLibTraitMethodInterceptor(dependencyMethodIndex, narrowedContext) {

  override def intercept(o: scala.Any, method: Method, objects: Array[AnyRef], methodProxy: MethodProxy): AnyRef = {
    if (factoryMethodIndex.contains(method)) {
      val wiringWithContext = factoryMethodIndex(method)
      val justExecutor = mkExecutor(objects, wiringWithContext)

      val executableOp = FactoryTools.mkExecutableOp(op.target, wiringWithContext.wireWith, op.origin)

      justExecutor.execute(executableOp)
    } else {
      super.intercept(o, method, objects, methodProxy)
    }
  }

  private def mkExecutor(arguments: Array[AnyRef], wiringWithContext: Factory.FactoryMethod): JustExecutor = {
    if (arguments.length != wiringWithContext.methodArguments.length) {
      throw new CgLibCallException(
        s"Divergence between constructor arguments count: ${arguments.toSeq} vs ${wiringWithContext.methodArguments} "
        , arguments.toSeq
        , wiringWithContext.methodArguments
      )
    }

    val providedValues = wiringWithContext.methodArguments.zip(arguments).toMap

    val unmatchedTypes = providedValues.filterNot {
      case (key, value) =>
        mirror.runtimeClass(key.tpe.tpe) match {
          case Some(runtimeClass) =>
            TypeUtil.isAssignableFrom(runtimeClass, value)
          case None =>
            false // here we cannot check the types so may let cglib try
        }
    }

    if (unmatchedTypes.nonEmpty) {
      throw new CgLibCallException(
        s"Divergence between constructor arguments types and provided values: $unmatchedTypes"
        , arguments.toSeq
        , wiringWithContext.methodArguments
      )
    }

    val extendedContext = narrowedContext.extend(providedValues)
    new JustExecutor {
      override def execute(step: ExecutableOp.WiringOp): AnyRef = {
        FactoryTools.interpret(executor.execute(extendedContext, step))
      }
    }
  }
}

protected[distage] object CgLibFactoryMethodInterceptor {
  sealed trait JustExecutor {
    def execute(step: ExecutableOp.WiringOp): AnyRef
  }
}


