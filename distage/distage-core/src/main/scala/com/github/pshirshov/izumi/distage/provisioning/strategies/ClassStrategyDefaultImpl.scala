package com.github.pshirshov.izumi.distage.provisioning.strategies

import com.github.pshirshov.izumi.distage.model.exceptions.InvalidPlanException
import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp
import com.github.pshirshov.izumi.distage.model.provisioning.{OpResult, ProvisioningContext}
import com.github.pshirshov.izumi.distage.model.provisioning.strategies.ClassStrategy

import scala.reflect.runtime.{currentMirror, universe}

class ClassStrategyDefaultImpl extends ClassStrategy {
  def instantiateClass(context: ProvisioningContext, op: ExecutableOp.WiringOp.InstantiateClass): Seq[OpResult.NewInstance] = {

    import op._

    val depMap = wiring.associations.map {
      key =>
        context.fetchKey(key.wireWith) match {
          case Some(dep) =>
            key.symbol -> dep
          case _ =>
            throw new InvalidPlanException(s"The impossible happened! Tried to instantiate class," +
              s" but the dependency has not been initialized: Class: $target, dependency: $key")
        }
    }.toMap

    val targetType = wiring.instanceType
    val refUniverse = currentMirror
    val refClass = refUniverse.reflectClass(targetType.tpe.typeSymbol.asClass)
    val ctor = targetType.tpe.decl(universe.termNames.CONSTRUCTOR).asMethod
    val refCtor = refClass.reflectConstructor(ctor)

    val orderedArgs = ctor.paramLists.head.map {
      key => depMap(key)
    }

    val instance = refCtor.apply(orderedArgs: _*)
    Seq(OpResult.NewInstance(target, instance))
  }
}

object ClassStrategyDefaultImpl {
  final val instance = new ClassStrategyDefaultImpl()
}