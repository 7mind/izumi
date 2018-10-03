package com.github.pshirshov.izumi.distage.provisioning.strategies

import com.github.pshirshov.izumi.distage.model.exceptions.{InvalidPlanException, MissingRefException, NoopProvisionerImplCalled, ProvisioningException}
import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp.WiringOp
import com.github.pshirshov.izumi.distage.model.provisioning.strategies.ClassStrategy
import com.github.pshirshov.izumi.distage.model.provisioning.{OpResult, ProvisioningKeyProvider}
import com.github.pshirshov.izumi.distage.model.reflection.SymbolIntrospector
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse._
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse.u._
import com.github.pshirshov.izumi.fundamentals.platform.language.Quirks
import com.github.pshirshov.izumi.fundamentals.reflection.{ReflectionUtil, TypeUtil}

class ClassStrategyDefaultImpl
(
  symbolIntrospector: SymbolIntrospector.Runtime
) extends ClassStrategy {

  import ClassStrategyDefaultImpl._

  def instantiateClass(context: ProvisioningKeyProvider, op: WiringOp.InstantiateClass): Seq[OpResult.NewInstance] = {
    val wiring = op.wiring
    val args = wiring.associations.map {
      key =>
        context.fetchKey(key.wireWith, key.isByName) match {
          case Some(dep) =>
            dep
          case _ =>
            throw new InvalidPlanException("The impossible happened! Tried to instantiate class," +
              s" but the dependency has not been initialized: dependency: ${key.wireWith} of class: $op")
        }
    }

    val instance = mkScala(context, op, args)
    Seq(OpResult.NewInstance(op.target, instance))
  }

  protected def mkScala(context: ProvisioningKeyProvider, op: WiringOp.InstantiateClass, args: Seq[Any]) = {
    val wiring = op.wiring
    val targetType = wiring.instanceType
    val symbol = targetType.tpe.typeSymbol

    if (symbol.isModule) { // don't re-instantiate scala objects
      mirror.reflectModule(symbol.asModule).instance
    } else {
      val (refClass, prefixInstance) = reflectClass(context, op, symbol)
      val ctorSymbol = symbolIntrospector.selectConstructorMethod(targetType)
      val refCtor = refClass.reflectConstructor(ctorSymbol)
      val hasByName = ctorSymbol.paramLists.exists(_.exists(v => v.isTerm && v.asTerm.isByNameParam))

      if (hasByName) {
        // this is a dirty workaround for crappy logic in JavaTransformingMethodMirror
        val fullArgs = prefixInstance match {
          case Some(value) =>
            value.asInstanceOf[AnyRef] +: args
          case None =>
            args
        }
        mkJava(targetType, fullArgs)
      } else {
        refCtor.apply(args: _*)
      }
    }
  }


  protected def reflectClass(context: ProvisioningKeyProvider, op: WiringOp.InstantiateClass, symbol: Symbol): (ClassMirror, Option[Any]) = {
    val wiring = op.wiring
    val targetType = wiring.instanceType
    if (!symbol.isStatic) {
      val typeRef = ReflectionUtil.toTypeRef(targetType.tpe)
        .getOrElse(throw new ProvisioningException(s"Expected TypeRefApi while processing $targetType, got ${targetType.tpe}", null))

      val prefix = typeRef.pre

      val module = if (prefix.termSymbol.isModule && prefix.termSymbol.isStatic) {
        Module.Static(mirror.reflectModule(prefix.termSymbol.asModule).instance)
      } else {
        val key = op.wiring.prefix match {
          case Some(value) =>
            value
          case None =>
            throw new MissingRefException(s"No prefix defined for key ${op.target} while processing $op", Set(op.target), None)
        }

        context.fetchUnsafe(key) match {
          case Some(value) =>
            Module.Prefix(value)
          case None =>
            throw new MissingRefException(s"Cannot get instance of prefix type $key while processing $op", Set(key), None)
        }
      }


      (mirror.reflect(module.instance).reflectClass(symbol.asClass), module.toPrefix)
    } else {
      (mirror.reflectClass(symbol.asClass), None)
    }
  }

  protected def mkJava(targetType: SafeType, args: Seq[Any]): Any = {
    val refUniverse = RuntimeDIUniverse.mirror
    val clazz = refUniverse
      .runtimeClass(targetType.tpe)
    val argValues = args.map(_.asInstanceOf[AnyRef])

    clazz
      .getDeclaredConstructors
      .toList
      .filter(_.getParameterCount == args.size)
      .find {
        c =>
          c.getParameterTypes.zip(argValues).forall({ case (exp, impl) => TypeUtil.isAssignableFrom(exp, impl) })
      } match {
      case Some(constructor) =>
        constructor.setAccessible(true)
        constructor.newInstance(argValues: _*)

      case None =>
        throw new ProvisioningException(s"Can't find constructor for $targetType", null)
    }
  }


}

object ClassStrategyDefaultImpl {

  sealed trait Module {
    def toPrefix: Option[Any]

    def instance: Any
  }

  object Module {

    case class Static(instance: Any) extends Module {
      override def toPrefix: Option[Any] = None
    }

    case class Prefix(instance: Any) extends Module {
      override def toPrefix: Option[Any] = Some(instance)
    }

  }

}


class ClassStrategyFailingImpl extends ClassStrategy {
  override def instantiateClass(context: ProvisioningKeyProvider, op: WiringOp.InstantiateClass): Seq[OpResult.NewInstance] = {
    Quirks.discard(context)
    throw new NoopProvisionerImplCalled(s"ClassStrategyFailingImpl does not support instantiation, failed op: $op", this)
  }
}
