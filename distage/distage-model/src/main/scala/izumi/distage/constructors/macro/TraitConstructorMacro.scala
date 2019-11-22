package izumi.distage.constructors.`macro`

import izumi.distage.constructors.{DebugProperties, TraitConstructor}
import izumi.distage.model.providers.ProviderMagnet
import izumi.distage.model.reflection.universe.StaticDIUniverse
import izumi.distage.reflection.{DependencyKeyProviderDefaultImpl, ReflectionProviderDefaultImpl, SymbolIntrospectorDefaultImpl}
import izumi.fundamentals.reflection.{AnnotationTools, ReflectionUtil, TrivialMacroLogger}

import scala.reflect.macros.blackbox

object TraitConstructorMacro {
  def mkTraitConstructor[T: c.WeakTypeTag](c: blackbox.Context): c.Expr[TraitConstructor[T]] =
    mkTraitConstructorImpl[T](c, generateUnsafeWeakSafeTypes = false)

  def mkTraitConstructorImpl[T: c.WeakTypeTag](c: blackbox.Context, generateUnsafeWeakSafeTypes: Boolean): c.Expr[TraitConstructor[T]] = {
    import c.universe._

    val macroUniverse = StaticDIUniverse(c)
    import macroUniverse.Association._
    import macroUniverse.Wiring._
    import macroUniverse._

    val symbolIntrospector = SymbolIntrospectorDefaultImpl.Static(macroUniverse)
    val keyProvider = DependencyKeyProviderDefaultImpl.Static(macroUniverse)(symbolIntrospector)
    val reflectionProvider = ReflectionProviderDefaultImpl.Static(macroUniverse)(keyProvider, symbolIntrospector)
    val logger = TrivialMacroLogger.make[this.type](c, DebugProperties.`izumi.debug.macro.distage.constructors`)

    val targetType = weakTypeOf[T]

    val SingletonWiring.AbstractSymbol(_, wireables, _) = reflectionProvider.symbolToWiring(SafeType(targetType))

    val (wireArgs, wireMethods) = wireables.map {
      case AbstractMethod(ctx, name, _, key) =>
        key.tpe.use { tpe =>
          val methodName: TermName = TermName(name)
          val argName: TermName = c.freshName(methodName)

          val mods = AnnotationTools.mkModifiers(u)(ctx.methodSymbol.annotations)

          q"$mods val $argName: $tpe" -> q"override val $methodName: $tpe = $argName"
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
