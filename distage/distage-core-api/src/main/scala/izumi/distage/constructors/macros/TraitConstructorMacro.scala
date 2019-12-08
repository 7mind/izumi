package izumi.distage.constructors.macros

import izumi.distage.constructors.{DebugProperties, TraitConstructor}
import izumi.distage.model.providers.ProviderMagnet
import izumi.distage.model.reflection.ReflectionProvider
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

    val targetType = ReflectionUtil.norm(c.universe: c.universe.type)(weakTypeOf[T])

    val macroUniverse = StaticDIUniverse(c)
    val tools = DIUniverseLiftables(macroUniverse)

    val reflectionProvider = ReflectionProviderDefaultImpl(macroUniverse)
    val logger = TrivialMacroLogger.make[this.type](c, DebugProperties.`izumi.debug.macro.distage.constructors`)

    val (associations, constructor) = mkTraitConstructorUnwrappedImpl(c)(macroUniverse)(tools, reflectionProvider, logger)(targetType)

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

  def mkTraitConstructorUnwrappedImpl(c: blackbox.Context)
                                     (macroUniverse: StaticDIUniverse.Aux[c.universe.type])
                                     (tools: DIUniverseLiftables[macroUniverse.type],
                                      reflectionProvider: ReflectionProvider.Aux[macroUniverse.type],
                                      logger: TrivialLogger)
                                     (targetType: c.Type): (List[macroUniverse.Association.Parameter], c.Tree) = {
    import c.universe._
    import macroUniverse.Wiring.SingletonWiring.AbstractSymbol

    object NonConstructible {
      def unapply(arg: List[Type]): Option[Type] = arg.collectFirst(isNonConstructibleType)
    }
    def isNonConstructibleType: PartialFunction[Type, Type] = {
      case RefinedType(NonConstructible(tpe), _) => tpe
      case tpe if tpe.typeSymbol.isParameter || tpe.typeSymbol.isFinal => tpe
    }

    isNonConstructibleType.lift(targetType).foreach {
      err =>
        c.abort(c.enclosingPosition, s"Cannot construct an implementation for $targetType: it contains a type parameter $err (${err.typeSymbol}) in type constructor position")
    }

    val AbstractSymbol(unsafeRet, wireables, _) = reflectionProvider.symbolToWiring(targetType)
    val (associations, wireArgs, wire0) = wireables.map(mkArgFromAssociation(c)(macroUniverse)(logger)(_)).unzip3
    val (wireMethods, _) = wire0.unzip

    val instantiate = newWithMethods(c)(targetType, wireMethods)
    val constructor = q"(..$wireArgs) => _root_.izumi.distage.constructors.TraitConstructor.wrapInitialization[$targetType](${tools.liftableSafeType(unsafeRet)})($instantiate)"

    (associations, constructor)
  }

  def newWithMethods(c: blackbox.Context)(targetType: c.universe.Type, methods: List[c.universe.Tree]): c.universe.Tree = {
    import c.universe._

    val parents = ReflectionUtil.intersectionTypeMembers[c.universe.type](targetType)
    if (methods.isEmpty) {
      q"new ..$parents {}"
    } else {
      q"new ..$parents { ..$methods }"
    }
  }

  def mkArgFromAssociation(c: blackbox.Context)
                          (u: StaticDIUniverse.Aux[c.universe.type])
                          (logger: TrivialLogger)
                          (association: u.Association): (u.Association.Parameter, c.universe.Tree, (c.universe.Tree, c.universe.TermName)) = {
    import c.universe._
    import u.Association._

    association match {
      case method: AbstractMethod =>
        val paramTpe = method.symbol.finalResultType.use(identity)
        val methodName = TermName(method.name)
        val freshArgName = c.freshName(methodName)
        // force by-name
        val byNameParamTpe = appliedType(definitions.ByNameParamClass, paramTpe)

        val parameter = method.asParameter
        logger.log(s"original method return: $paramTpe, after by-name: $byNameParamTpe, $parameter")

        (parameter, q"val $freshArgName: $byNameParamTpe", q"final lazy val $methodName: $paramTpe = $freshArgName" -> freshArgName)
      case parameter: Parameter =>
        val paramTpe = parameter.symbol.finalResultType.use(identity)
        val methodName = TermName(parameter.name)
        val freshArgName = c.freshName(TermName(parameter.name))

        (parameter, q"val $freshArgName: $paramTpe", q"final lazy val $methodName: $paramTpe = $freshArgName" -> freshArgName)
    }
  }

}
