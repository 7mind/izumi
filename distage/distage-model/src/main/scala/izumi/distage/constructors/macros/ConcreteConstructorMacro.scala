package izumi.distage.constructors.macros

import izumi.distage.constructors.{ConcreteConstructor, DebugProperties}
import izumi.distage.model.providers.ProviderMagnet
import izumi.distage.model.reflection.macros.ProviderMagnetMacro0
import izumi.distage.model.reflection.universe.StaticDIUniverse
import izumi.distage.reflection.ReflectionProviderDefaultImpl
import izumi.fundamentals.reflection.{ReflectionUtil, TrivialMacroLogger}

import scala.reflect.macros.blackbox

object ConcreteConstructorMacro {

  def mkConcreteConstructor[T: c.WeakTypeTag](c: blackbox.Context): c.Expr[ConcreteConstructor[T]] = mkConcreteConstructorImpl[T](c, generateUnsafeWeakSafeTypes = false)

  def mkConcreteConstructorImpl[T: c.WeakTypeTag](c: blackbox.Context, generateUnsafeWeakSafeTypes: Boolean): c.Expr[ConcreteConstructor[T]] = {
    import c.universe._

    val macroUniverse = StaticDIUniverse(c)

    val reflectionProvider = ReflectionProviderDefaultImpl(macroUniverse)
    val logger = TrivialMacroLogger.make[this.type](c, DebugProperties.`izumi.debug.macro.distage.constructors`)

    val targetType = ReflectionUtil.norm(c.universe: c.universe.type)(weakTypeOf[T])

    if (!reflectionProvider.isConcrete(targetType)) {
      c.abort(c.enclosingPosition,
        s"""Tried to derive constructor function for class $targetType, but the class is an
           |abstract class or a trait! Only concrete classes (`class` keyword) are supported""".stripMargin)
    }

    // ???
    targetType match {
      case t: SingletonTypeApi =>
        val providerMagnet = symbolOf[ProviderMagnet.type].asClass.module
        val term = t match {
          case t: ThisTypeApi => This(t.sym)
          case _ => q"${t.termSymbol}"
        }
        return c.Expr[ConcreteConstructor[T]] {
          q"{ new ${weakTypeOf[ConcreteConstructor[T]]}($providerMagnet.pure($term)) }"
        }
      case _ =>
    }

    val paramLists = reflectionProvider.constructorParameterLists(targetType)
    val fnArgsNamesLists = paramLists.map(_.map {
      param =>
        val name = c.freshName(TermName(param.name))
        val paramTpe = param.symbol.finalResultType.use(identity)
        (param -> q"val $name: $paramTpe", name)
    })

    val (associations, args) = fnArgsNamesLists.flatten.map(_._1).unzip
    val argNamesLists = fnArgsNamesLists.map(_.map(_._2))

    val constructor = q"(..$args) => new $targetType(...$argNamesLists)"

    val provided: c.Expr[ProviderMagnet[T]] = {
      val providerMagnetMacro = new ProviderMagnetMacro0[c.type](c)
      providerMagnetMacro.generateProvider[T](
        associations.asInstanceOf[List[providerMagnetMacro.macroUniverse.Association.Parameter]],
        constructor,
        generateUnsafeWeakSafeTypes,
      )
    }
    val res = c.Expr[ConcreteConstructor[T]] {
      q"{ new ${weakTypeOf[ConcreteConstructor[T]]}($provided) }"
    }
    logger.log(s"Final syntax tree of concrete constructor for $targetType:\n$res")

    res
  }
}

