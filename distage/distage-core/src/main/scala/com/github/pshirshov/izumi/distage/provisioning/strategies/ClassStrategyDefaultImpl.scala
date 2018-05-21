package com.github.pshirshov.izumi.distage.provisioning.strategies

import com.github.pshirshov.izumi.distage.model.exceptions.InvalidPlanException
import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp.WiringOp
import com.github.pshirshov.izumi.distage.model.provisioning.strategies.ClassStrategy
import com.github.pshirshov.izumi.distage.model.provisioning.{OpResult, ProvisioningKeyProvider}
import com.github.pshirshov.izumi.distage.model.reflection.SymbolIntrospector
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse

//import scala.reflect.runtime.{currentMirror, universe}

class ClassStrategyDefaultImpl
(
  symbolIntrospector: SymbolIntrospector.Runtime
) extends ClassStrategy {
  def instantiateClass(context: ProvisioningKeyProvider, op: WiringOp.InstantiateClass): Seq[OpResult.NewInstance] = {

    import op._

    val targetType = wiring.instanceType

    val args = wiring.associations.map {
      key =>
        context.fetchKey(key.wireWith) match {
          case Some(dep) =>
            dep
          case _ =>
            throw new InvalidPlanException(s"The impossible happened! Tried to instantiate class," +
              s" but the dependency has not been initialized: dependency: ${key.wireWith} of class: $target")
        }
    }

    val refUniverse = RuntimeDIUniverse.mirror
    val refClass = refUniverse.reflectClass(targetType.tpe.typeSymbol.asClass)

    val ctorSymbol = symbolIntrospector.selectConstructorMethod(targetType)
    val refCtor = refClass.reflectConstructor(ctorSymbol)

    val instance = refCtor.apply(args: _*)
    Seq(OpResult.NewInstance(target, instance))
  }
}

