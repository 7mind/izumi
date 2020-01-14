package izumi.distage.constructors.macros

import izumi.distage.constructors.{DebugProperties, TraitConstructor}
import izumi.distage.model.providers.ProviderMagnet
import izumi.distage.model.reflection.ReflectionProvider
import izumi.distage.model.reflection.macros.ProviderMagnetMacro0
import izumi.distage.model.reflection.universe.StaticDIUniverse
import izumi.distage.reflection.ReflectionProviderDefaultImpl
import izumi.fundamentals.platform.console.TrivialLogger
import izumi.fundamentals.reflection.{ReflectionUtil, TrivialMacroLogger}

import scala.reflect.macros.blackbox

object TraitConstructorMacro {

  def mkTraitConstructor[T: c.WeakTypeTag](c: blackbox.Context): c.Expr[TraitConstructor[T]] = {
    import c.universe._

    val targetType = ReflectionUtil.norm(c.universe: c.universe.type)(weakTypeOf[T])

    val macroUniverse = StaticDIUniverse(c)

    val reflectionProvider = ReflectionProviderDefaultImpl(macroUniverse)
    val logger = TrivialMacroLogger.make[this.type](c, DebugProperties.`izumi.debug.macro.distage.constructors`)

    val (associations, constructor) = mkTraitConstructorUnwrapped(c)(macroUniverse)(reflectionProvider, logger)(targetType)

    val provider: c.Expr[ProviderMagnet[T]] = {
      val providerMagnetMacro = new ProviderMagnetMacro0[c.type](c)
      providerMagnetMacro.generateProvider[T](
        parameters = associations.asInstanceOf[List[providerMagnetMacro.macroUniverse.Association.Parameter]],
        fun = constructor,
        isGenerated = true
      )
    }

    val res = c.Expr[TraitConstructor[T]] {
      q"""{ new ${weakTypeOf[TraitConstructor[T]]}($provider) }"""
    }
    logger.log(s"Final syntax tree of trait $targetType:\n$res")

    res
  }

  def mkTraitConstructorUnwrapped(c: blackbox.Context)
                                 (macroUniverse: StaticDIUniverse.Aux[c.universe.type])
                                 (reflectionProvider: ReflectionProvider.Aux[macroUniverse.type],
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

    val AbstractSymbol(_, wireables, prefix@_) = reflectionProvider.symbolToWiring(targetType)
    val (traitAssociations, traitCtorArgs, wireMethodsCtorArgNames) = wireables.map(mkArgFromAssociation(c)(macroUniverse)(logger)(_)).unzip3
    val (wireMethods, _) = wireMethodsCtorArgNames.unzip
    val (ctorAssociations, classCtorArgs, ctorParams) = ClassConstructorMacro.mkClassConstructorUnwrappedImpl(c)(macroUniverse)(reflectionProvider, logger)(targetType)

    val ctorLambda = newWithMethods(c)(targetType, ctorParams, wireMethods)
    val constructor = q"(..${classCtorArgs ++ traitCtorArgs}) => _root_.izumi.distage.constructors.TraitConstructor.wrapInitialization[$targetType]($ctorLambda)"

    (ctorAssociations ++ traitAssociations, constructor)
  }

  def newWithMethods(c: blackbox.Context)(targetType: c.universe.Type, arguments: List[List[c.universe.TermName]], methods: List[c.universe.Tree]): c.universe.Tree = {
    import c.universe._

    val parents = ReflectionUtil.intersectionTypeMembers[c.universe.type](targetType)
    parents match {
      case parent :: Nil =>
        if (methods.isEmpty) {
          q"new $parent(...$arguments) {}"
        } else {
          q"new $parent(...$arguments) { ..$methods }"
        }
      case _ =>
        if (arguments.nonEmpty) {
          c.abort(c.enclosingPosition,
            s"""Unsupported case: intersection type containing an abstract class.
               |Please manually create an abstract class with the added traits.
               |When trying to create a TraitConstructor for $targetType""".stripMargin)
        } else {
          if (methods.isEmpty) {
            q"new ..$parents {}"
          } else {
            q"new ..$parents { ..$methods }"
          }
        }
    }
  }

  def mkArgFromAssociation(c: blackbox.Context)
                          (u: StaticDIUniverse.Aux[c.universe.type])
                          (logger: TrivialLogger)
                          (association: u.Association
                          // parameter: u.Association.Parameter, ctorArgument: u.u.Tree, traitMethodImpl: u.u.Tree, ctorArgumentName: u.u.TermName
                          ): (u.Association.Parameter, u.u.Tree, (u.u.Tree, u.u.TermName)) = {
    import c.universe._
    import u.Association._

    association match {
      case method: AbstractMethod =>
        val paramTpe = method.symbol.finalResultType
        val methodName = TermName(method.name)
        val freshArgName = c.freshName(methodName)
        // force by-name
        val byNameParamTpe = appliedType(definitions.ByNameParamClass, paramTpe)

        val parameter = method.asParameter
        logger.log(s"original method return: $paramTpe, after by-name: $byNameParamTpe, $parameter")

        (parameter, q"val $freshArgName: $byNameParamTpe", q"final lazy val $methodName: $paramTpe = $freshArgName" -> freshArgName)
      case parameter: Parameter =>
        val paramTpe = parameter.symbol.finalResultType
        val methodName = TermName(parameter.name)
        val freshArgName = c.freshName(TermName(parameter.name))

        (parameter, q"val $freshArgName: $paramTpe", q"final lazy val $methodName: $paramTpe = $freshArgName" -> freshArgName)
    }
  }

}
