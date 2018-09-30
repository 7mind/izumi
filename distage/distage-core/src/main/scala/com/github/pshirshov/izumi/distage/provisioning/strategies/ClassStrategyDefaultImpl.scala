package com.github.pshirshov.izumi.distage.provisioning.strategies

import com.github.pshirshov.izumi.distage.model.exceptions.{InvalidPlanException, NoopProvisionerImplCalled, ProvisioningException}
import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp.WiringOp
import com.github.pshirshov.izumi.distage.model.provisioning.strategies.ClassStrategy
import com.github.pshirshov.izumi.distage.model.provisioning.{OpResult, ProvisioningKeyProvider}
import com.github.pshirshov.izumi.distage.model.reflection
import com.github.pshirshov.izumi.distage.model.reflection.{SymbolIntrospector, universe}
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse
import com.github.pshirshov.izumi.fundamentals.platform.language.Quirks

class ClassStrategyDefaultImpl
(
  symbolIntrospector: SymbolIntrospector.Runtime
) extends ClassStrategy {
  def instantiateClass(context: ProvisioningKeyProvider, op: WiringOp.InstantiateClass): Seq[OpResult.NewInstance] = {

    import op._

    val targetType = wiring.instanceType

    val args = wiring.associations.map {
      key =>
        context.fetchKey(key.wireWith, key.isByName) match {
          case Some(dep) =>
            dep
          case _ =>
            throw new InvalidPlanException("The impossible happened! Tried to instantiate class," +
              s" but the dependency has not been initialized: dependency: ${key.wireWith} of class: $target")
        }
    }

    val instance = mkScala(targetType, args)
    Seq(OpResult.NewInstance(target, instance))
  }

  private def mkScala(targetType: reflection.universe.RuntimeDIUniverse.SafeType, args: Seq[Any]) = {
    val refUniverse = RuntimeDIUniverse.mirror
    val symbol = targetType.tpe.typeSymbol
    val refClass = refUniverse.reflectClass(symbol.asClass)
    val ctorSymbol = symbolIntrospector.selectConstructorMethod(targetType)
    val refCtor = refClass.reflectConstructor(ctorSymbol)

    val hasByName = ctorSymbol.paramLists.exists(_.exists(v => v.isTerm && v.asTerm.isByNameParam))

    if (symbol.isModule) { // don't re-instantiate scala objects
      refUniverse.reflectModule(symbol.asModule).instance
    } else {
      if (hasByName) { // this is a dirty workaround for crappy logic in JavaTransformingMethodMirror
        mkJava(targetType, args)
      } else {
        refCtor.apply(args: _*)
      }
    }
  }

  private def mkJava(targetType: universe.RuntimeDIUniverse.SafeType, args: Seq[Any]) = {
    val refUniverse = RuntimeDIUniverse.mirror
    val argClasses = args.map(_.getClass)
    val clazz = refUniverse
      .runtimeClass(targetType.tpe)

    clazz
      .getDeclaredConstructors
      .toList
      .filter(_.getParameterCount == args.size)
      .find {
        c =>
          c.getParameterTypes.zip(argClasses).forall({ case (exp, impl) => exp.isAssignableFrom(impl) })
      } match {
      case Some(constructor) =>
        constructor.setAccessible(true)
        constructor.newInstance(args.map(_.asInstanceOf[AnyRef]): _*)

      case None =>
        throw new ProvisioningException(s"Can't find constructor for $targetType", null)
    }
  }
}

class ClassStrategyFailingImpl extends ClassStrategy {
  override def instantiateClass(context: ProvisioningKeyProvider, op: WiringOp.InstantiateClass): Seq[OpResult.NewInstance] = {
    Quirks.discard(context)
    throw new NoopProvisionerImplCalled(s"ClassStrategyFailingImpl does not support instantiation, failed op: $op", this)
  }
}
