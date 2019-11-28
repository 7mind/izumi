package izumi.distage.constructors.`macro`

import izumi.distage.constructors.{DebugProperties, TraitConstructor}
import izumi.distage.model.providers.ProviderMagnet
import izumi.distage.model.reflection.universe.StaticDIUniverse
import izumi.distage.reflection.ReflectionProviderDefaultImpl
import izumi.fundamentals.reflection.{AnnotationTools, ReflectionUtil, TrivialMacroLogger}

import scala.reflect.macros.blackbox

object TraitConstructorMacro {
  def mkTraitConstructor[T: c.WeakTypeTag](c: blackbox.Context): c.Expr[TraitConstructor[T]] =
    mkTraitConstructorImpl[T](c, generateUnsafeWeakSafeTypes = false)

  def mkTraitConstructorImpl[T: c.WeakTypeTag](c: blackbox.Context, generateUnsafeWeakSafeTypes: Boolean): c.Expr[TraitConstructor[T]] = {
    import c.universe._

    def nonConstructibleType: PartialFunction[Type, Type] = {
      object NonConstructible {
        def unapply(arg: List[Type]): Option[Type] = arg.collectFirst(nonConstructibleType)
      }

      {
        case RefinedType(NonConstructible(tpe), _) => tpe
        case tpe if tpe.typeSymbol.isParameter || tpe.typeSymbol.isFinal => tpe
      }
    }

    val targetType = ReflectionUtil.norm(c.universe: c.universe.type)(weakTypeOf[T])

    nonConstructibleType.lift(targetType).foreach {
      err =>
        c.abort(c.enclosingPosition, s"Cannot construct an implementation for $targetType: it contains a type parameter $err (${err.typeSymbol}) in type constructor position")
    }

    val macroUniverse = StaticDIUniverse(c)
    import macroUniverse.Association._
    import macroUniverse.Wiring._
    import macroUniverse._

    val reflectionProvider = ReflectionProviderDefaultImpl.Static(macroUniverse)
    val logger = TrivialMacroLogger.make[this.type](c, DebugProperties.`izumi.debug.macro.distage.constructors`)

    val SingletonWiring.AbstractSymbol(_, wireables, _) = reflectionProvider.symbolToWiring(targetType)

    val (wireArgs, wireMethods) = wireables.map {
      case AbstractMethod(ctx, name, _, key) =>
        key.tpe.use { tpe =>
          val methodName: TermName = TermName(name)
          val argName: TermName = c.freshName(methodName)

          val mods = AnnotationTools.mkModifiers(u)(ctx.methodSymbol.annotations)

          q"$mods val $argName: $tpe" -> q"final val $methodName: $tpe = $argName"
        }
    }.unzip

    val parents = ReflectionUtil.intersectionTypeMembers[c.universe.type](targetType)

    val instantiate = if (wireMethods.isEmpty)
      q"new ..$parents {}"
    else
      q"new ..$parents { ..$wireMethods }"

    val constructorDef =
      q"""
      ${
        if (wireArgs.nonEmpty)
          q"def constructor(..$wireArgs): $targetType = ($instantiate): $targetType"
        else
          q"def constructor: $targetType = ($instantiate): $targetType"
      }
      """

    val providerMagnet = symbolOf[ProviderMagnet.type].asClass.module

    val provided =
      if (generateUnsafeWeakSafeTypes)
        q"{ $providerMagnet.generateUnsafeWeakSafeTypes[$targetType](constructor _) }"
      else
        q"{ $providerMagnet.apply[$targetType](constructor _) }"

    val res = c.Expr[TraitConstructor[T]] {
      q"""
          {
          $constructorDef

          new ${weakTypeOf[TraitConstructor[T]]}($provided)
          }
       """
    }
    logger.log(s"Final syntax tree of trait $targetType:\n$res")

    res
  }
}
