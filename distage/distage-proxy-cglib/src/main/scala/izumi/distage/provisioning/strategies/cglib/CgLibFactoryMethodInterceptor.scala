package izumi.distage.provisioning.strategies.cglib

import java.lang.reflect.Method

import izumi.distage.model.plan.ExecutableOp
import izumi.distage.model.provisioning.strategies.TraitIndex
import izumi.distage.model.provisioning.{ProvisioningKeyProvider, WiringExecutor}
import izumi.distage.model.reflection.universe.{MirrorProvider, RuntimeDIUniverse}
import net.sf.cglib.proxy.MethodProxy

protected[distage] class CgLibFactoryMethodInterceptor
(
  factoryMethodIndex: Map[Method, RuntimeDIUniverse.Wiring.Factory.FactoryMethod]
, dependencyMethodIndex: TraitIndex
, narrowedContext: ProvisioningKeyProvider
, executor: WiringExecutor
//, op: WiringOp.InstantiateFactory
, mirror: MirrorProvider
) extends CgLibTraitMethodInterceptor(dependencyMethodIndex, narrowedContext) {

  override def intercept(o: scala.Any, method: Method, objects: Array[AnyRef], methodProxy: MethodProxy): AnyRef = {
//    if (factoryMethodIndex.contains(method)) {
//      val wiringWithContext = factoryMethodIndex(method)
//      val justExecutor = mkExecutor(objects, wiringWithContext)
//
//      val executableOp = FactoryTools.mkExecutableOp(op.target, wiringWithContext.wireWith, op.origin)
//
//      justExecutor.execute(executableOp)
//    } else {
//      super.intercept(o, method, objects, methodProxy)
//    }
    ???
  }
}

protected[distage] object CgLibFactoryMethodInterceptor {
  sealed trait JustExecutor {
    def execute(step: ExecutableOp.WiringOp): AnyRef
  }
}


