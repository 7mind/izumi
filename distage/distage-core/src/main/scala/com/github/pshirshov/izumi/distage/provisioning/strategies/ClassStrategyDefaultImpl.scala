package com.github.pshirshov.izumi.distage.provisioning.strategies

import java.lang.reflect.Method

import com.github.pshirshov.izumi.distage.model.exceptions._
import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp.WiringOp
import com.github.pshirshov.izumi.distage.model.provisioning.strategies.ClassStrategy
import com.github.pshirshov.izumi.distage.model.provisioning.{OpResult, ProvisioningKeyProvider}
import com.github.pshirshov.izumi.distage.model.reflection.SymbolIntrospector
import com.github.pshirshov.izumi.distage.model.reflection.universe.MirrorProvider
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse._
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse.u._
import com.github.pshirshov.izumi.fundamentals.platform.language.Quirks
import com.github.pshirshov.izumi.fundamentals.reflection.{ReflectionUtil, TypeUtil}

case class Dep(key: DIKey, value: Any)

class ClassStrategyDefaultImpl
(
  symbolIntrospector: SymbolIntrospector.Runtime,
  mirrorProvider: MirrorProvider,
) extends ClassStrategy {

  import ClassStrategyDefaultImpl._

  def instantiateClass(context: ProvisioningKeyProvider, op: WiringOp.InstantiateClass): Seq[OpResult.NewInstance] = {
    val wiring = op.wiring
    val args = wiring.associations.map {
      key =>
        context.fetchKey(key.wireWith, key.isByName) match {
          case Some(dep) =>
            Dep(key.wireWith, dep)
          case _ =>
            throw new InvalidPlanException("The impossible happened! Tried to instantiate class," +
              s" but the dependency has not been initialized: dependency: ${key.wireWith} of class: $op")
        }
    }

    val instance = mkScala(context, op, args)
    Seq(OpResult.NewInstance(op.target, instance))
  }


  protected def mkScala(context: ProvisioningKeyProvider, op: WiringOp.InstantiateClass, args: Seq[Dep]): Any = {
    val wiring = op.wiring
    val targetType = wiring.instanceType
    val symbol = targetType.tpe.typeSymbol

    if (symbol.isModule) { // don't re-instantiate scala objects
      mirrorProvider.mirror.reflectModule(symbol.asModule).instance
    } else {
      val ctorSymbol = symbolIntrospector.selectConstructorMethod(targetType)
      val hasByName = ctorSymbol.exists(symbolIntrospector.hasByNameParameter)

      val (refClass, prefixInstance) = reflectClass(context, op, symbol)

      if (hasByName) {
        // this is a dirty workaround for crappy logic in JavaTransformingMethodMirror
        mkJava(targetType, prefixInstance, args)
      } else {
        val refCtor = refClass.reflectConstructor(ctorSymbol.getOrElse(
          throw new MissingConstructorException(s"Missing constructor in $targetType")
        ))
        val values = args.map(_.value)
        refCtor.apply(values: _*)
      }
    }
  }


  protected def reflectClass(context: ProvisioningKeyProvider, op: WiringOp.InstantiateClass, symbol: Symbol): (ClassMirror, Option[Any]) = {
    val wiring = op.wiring
    val targetType = wiring.instanceType
    if (!symbol.isStatic) {
      val typeRef = ReflectionUtil.toTypeRef[u.type](targetType.tpe)
        .getOrElse(throw new ProvisioningException(s"Expected TypeRefApi while processing $targetType, got ${targetType.tpe}", null))

      val prefix = typeRef.pre

      val module = if (prefix.termSymbol.isModule && prefix.termSymbol.isStatic) {
        Module.Static(mirrorProvider.mirror.reflectModule(prefix.termSymbol.asModule).instance)
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


      (mirrorProvider.mirror.reflect(module.instance).reflectClass(symbol.asClass), module.toPrefix)
    } else {
      (mirrorProvider.mirror.reflectClass(symbol.asClass), None)
    }
  }

  protected def mkJava(targetType: SafeType, prefix: Option[Any], args: Seq[Dep]): Any = {
    val clazz = mirrorProvider.runtimeClass(targetType)
    val argValues = prefix.map(_.asInstanceOf[AnyRef]).toSeq ++ args
      .map {
        case Dep(key, value) =>
          unbox(key, value)
      }

    val allConstructors = clazz
      .getDeclaredConstructors
      .toList

    val sameArityConstructors = allConstructors
      .filter(_.getParameterCount == argValues.size)

    val matchingConstructors = sameArityConstructors
      .find {
        c =>
          c.getParameterTypes.toList.zip(argValues).forall({ case (exp, impl) => TypeUtil.isAssignableFrom(exp, impl) })
      }

    matchingConstructors match {
      case Some(constructor) =>
        constructor.setAccessible(true)
        constructor.newInstance(argValues: _*)

      case None =>
        import com.github.pshirshov.izumi.fundamentals.platform.strings.IzString._

        val discrepancies = sameArityConstructors
          .map {
            c =>
              val repr = c.getParameterTypes.toList.zip(argValues).map({ case (exp, impl) => s"expected: $exp, have: ${impl.getClass}, matches: ${TypeUtil.isAssignableFrom(exp, impl)}" }).niceList().shift(2)

              s"$c: $repr"
          }

        throw new ProvisioningException(
          s"""Can't find constructor for $targetType, signature: ${argValues.map(_.getClass)}
             |issues: ${discrepancies.niceList().shift(2)}
           """.stripMargin, null)
    }
  }

  protected def unbox(info: DIKey, value: Any): AnyRef = {
    val tpe = info.tpe.tpe
    if (tpe.typeSymbol.isClass && tpe.typeSymbol.asClass.isDerivedValueClass) {
      val u = getUnboxMethod(tpe)
      u.invoke(value)
    } else {
      value.asInstanceOf[AnyRef]
    }
  }

  protected def getUnboxMethod(info: Type): Method = {
    val symbol = info.typeSymbol.asType
    val fields@(field :: _) = symbol.toType.decls.collect { case ts: TermSymbol if ts.isParamAccessor && ts.isMethod => ts }.toList
    assert(fields.length == 1, s"$symbol: $fields")
    mirrorProvider.mirror.runtimeClass(symbol.asClass).getDeclaredMethod(field.name.toString)
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
