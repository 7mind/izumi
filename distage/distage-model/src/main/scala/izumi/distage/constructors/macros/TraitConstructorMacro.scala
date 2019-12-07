package izumi.distage.constructors.macros

import izumi.distage.constructors.{DebugProperties, TraitConstructor}
import izumi.distage.model.providers.ProviderMagnet
import izumi.distage.model.reflection.macros.{DIUniverseLiftables, ProviderMagnetMacro0}
import izumi.distage.model.reflection.universe.StaticDIUniverse
import izumi.distage.reflection.ReflectionProviderDefaultImpl
import izumi.fundamentals.platform.console.TrivialLogger
import izumi.fundamentals.reflection.{ReflectionUtil, TrivialMacroLogger}

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
    import macroUniverse.Wiring.SingletonWiring.AbstractSymbol
    val tools = DIUniverseLiftables(macroUniverse)

    val reflectionProvider = ReflectionProviderDefaultImpl(macroUniverse)
    val logger = TrivialMacroLogger.make[this.type](c, DebugProperties.`izumi.debug.macro.distage.constructors`)

    val AbstractSymbol(unsafeRet, wireables, _) = reflectionProvider.symbolToWiring(targetType)
    val (associations, wireArgs, wireMethods) = mkTraitArgMethods(c)(macroUniverse)(logger, wireables)

    val parents = ReflectionUtil.intersectionTypeMembers[c.universe.type](targetType)

    val instantiate = if (wireMethods.isEmpty) {
      q"new ..$parents {}"
    } else {
      q"new ..$parents { ..$wireMethods }"
    }

    val constructor = {
      q"""
      ${
        if (wireArgs.nonEmpty)
          q"(..$wireArgs) => _root_.izumi.distage.constructors.TraitConstructor.wrapInitialization(${tools.liftableSafeType(unsafeRet)})($instantiate): $targetType"
        else
          q"() => _root_.izumi.distage.constructors.TraitConstructor.wrapInitialization(${tools.liftableSafeType(unsafeRet)})($instantiate): $targetType"
      }
      """
    }

    val provided: c.Expr[ProviderMagnet[T]] = {
      val providerMagnetMacro = new ProviderMagnetMacro0[c.type](c)
      providerMagnetMacro.generateProvider[T](
        parameters = associations.asInstanceOf[List[providerMagnetMacro.macroUniverse.Association.Parameter]],
        fun = constructor,
        generateUnsafeWeakSafeTypes = generateUnsafeWeakSafeTypes,
        isGenerated = true
      )
    }

    val res = c.Expr[TraitConstructor[T]] {
      q"""{ new ${weakTypeOf[TraitConstructor[T]]}($provided) }"""
    }
    logger.log(s"Final syntax tree of trait $targetType:\n$res")

    res
  }

  def mkTraitArgMethods(c: blackbox.Context)
                       (u: StaticDIUniverse.Aux[c.universe.type])
                       (logger: TrivialLogger, wireables: List[u.Association.AbstractMethod]): (List[u.Association.Parameter], List[c.universe.Tree], List[c.universe.Tree]) = {
    import c.universe._
    import u.Association._

    wireables.map {
      case m @ AbstractMethod(symbol, _) =>
        val paramTpe = symbol.finalResultType.use(identity)
        val methodName: TermName = TermName(symbol.name)
        val argName: TermName = c.freshName(methodName)
        // force by-name
        val byNameParamTpe = appliedType(definitions.ByNameParamClass, paramTpe)

        val parameter = m.asParameter
        logger.log(s"original method return: $paramTpe, after by-name: $byNameParamTpe, $parameter")

        (parameter, q"val $argName: $byNameParamTpe", q"final lazy val $methodName: $paramTpe = $argName")
    }.unzip3
  }
}
