package izumi.distage.constructors.`macro`

import izumi.distage.constructors.{ConcreteConstructor, DebugProperties}
import izumi.distage.model.providers.ProviderMagnet
import izumi.distage.model.reflection.universe.StaticDIUniverse
import izumi.distage.reflection.{DependencyKeyProviderDefaultImpl, ReflectionProviderDefaultImpl, SymbolIntrospectorDefaultImpl}
import izumi.fundamentals.reflection.{AnnotationTools, TrivialMacroLogger}

import scala.reflect.macros.blackbox

object ConcreteConstructorMacro {

  def mkConcreteConstructor[T: c.WeakTypeTag](c: blackbox.Context): c.Expr[ConcreteConstructor[T]] = mkConcreteConstructorImpl[T](c, generateUnsafeWeakSafeTypes = false)

  def mkConcreteConstructorImpl[T: c.WeakTypeTag](c: blackbox.Context, generateUnsafeWeakSafeTypes: Boolean): c.Expr[ConcreteConstructor[T]] = {
    import c.universe._

    val macroUniverse = StaticDIUniverse(c)
    import macroUniverse._

    val symbolIntrospector = SymbolIntrospectorDefaultImpl.Static(macroUniverse)
    val keyProvider = DependencyKeyProviderDefaultImpl.Static(macroUniverse)(symbolIntrospector)
    val reflectionProvider = ReflectionProviderDefaultImpl.Static(macroUniverse)(keyProvider, symbolIntrospector)
    val logger = TrivialMacroLogger.make[this.type](c, DebugProperties.`izumi.debug.macro.distage.constructors`)

    val targetType = weakTypeOf[T]

    if (!symbolIntrospector.isConcrete(SafeType(targetType))) {
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

    val paramLists = reflectionProvider.constructorParameterLists(SafeType(targetType))

    val fnArgsNamesLists = paramLists.map(_.map {
      p =>
        val name = c.freshName(TermName(p.name))

        val mods = AnnotationTools.mkModifiers(u)(p.context.symbol.annotations)

        p.wireWith.tpe.use {
          tpe =>
            q"$mods val $name: $tpe" -> name
        }

    })

    val args = fnArgsNamesLists.flatten.unzip._1
    val argNamesLists = fnArgsNamesLists.map(_.map(_._2))

    val fn = q"(..$args) => new $targetType(...$argNamesLists)"

    val providerMagnet = symbolOf[ProviderMagnet.type].asClass.module

    val provided =
      if (generateUnsafeWeakSafeTypes)
        q"{ $providerMagnet.generateUnsafeWeakSafeTypes[$targetType]($fn) }"
      else
        q"{ $providerMagnet.apply[$targetType]($fn) }"

    val res = c.Expr[ConcreteConstructor[T]] {
      q"{ new ${weakTypeOf[ConcreteConstructor[T]]}($provided) }"
    }
    logger.log(s"Final syntax tree of concrete constructor for $targetType:\n$res")

    res
  }
}

