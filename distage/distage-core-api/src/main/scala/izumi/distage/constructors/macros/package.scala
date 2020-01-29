package izumi.distage.constructors

import izumi.distage.model.providers.ProviderMagnet
import izumi.distage.model.reflection.ReflectionProvider
import izumi.distage.model.reflection.macros.DIUniverseLiftables
import izumi.distage.model.reflection.universe.{RuntimeDIUniverse, StaticDIUniverse}
import izumi.fundamentals.platform.console.TrivialLogger
import izumi.fundamentals.reflection.ReflectionUtil

import scala.reflect.macros.blackbox

package object macros {

  private[macros] def requireConcreteTypeConstructor(c: blackbox.Context)(macroName: String, tpe: c.Type): Unit = {
    if (!ReflectionUtil.allPartsStrong(tpe.dealias.typeConstructor)) {
      c.abort(c.enclosingPosition,
        s"""$macroName: Can't generate constructor for $tpe:
           |Type constructor is an unresolved type parameter `${tpe.dealias.typeConstructor}`.
           |Did you forget to put a $macroName context bound on the ${tpe.dealias.typeConstructor}, such as [${tpe.dealias.typeConstructor}: $macroName]?
           |""".stripMargin)
    }
  }

  abstract class ConstructorMacros {

    val c: blackbox.Context
    val u: StaticDIUniverse.Aux[c.universe.type]

    import c.universe._

    def mkCtorArgument(association: u.Association): CtorArgument = {
      val freshArgName = c.freshName(TermName(association.name))
      CtorArgument(association.asParameter, q"val $freshArgName: ${association.asParameterTpe}", freshArgName)
    }

    case class CtorArgument(parameter: u.Association.Parameter, ctorArgument: u.u.Tree, ctorArgumentName: u.u.TermName) {
      def traitMethodImpl = q"final lazy val ${parameter.termName}: ${parameter.tpe} = $ctorArgumentName"
    }
    object CtorArgument {
      implicit def unzip3: CtorArgument => (u.Association.Parameter, u.u.Tree, u.u.TermName) = {
        case CtorArgument(parameter, ctorArgument, ctorArgumentName) => (parameter, ctorArgument, ctorArgumentName)
      }
    }

    def mkTraitMethod(method: u.Association.AbstractMethod): TraitMethod = {
      val methodName = TermName(method.name)
      val freshArgName = c.freshName(methodName)
      TraitMethod(method.asParameter, q"val $freshArgName: ${method.asParameterTpe}", q"final lazy val $methodName: ${method.tpe} = $freshArgName")
    }

    case class TraitMethod(parameter: u.Association.Parameter, ctorArgument: u.u.Tree, traitMethodImpl: u.u.Tree)
    object TraitMethod {
      implicit def unzip3: TraitMethod => (u.Association.Parameter, u.u.Tree, u.u.Tree) = {
        case TraitMethod(parameter, ctorArgument, traitMethodImpl) => (parameter, ctorArgument, traitMethodImpl)
      }
    }

    def mkClassConstructorProvider[T: c.WeakTypeTag](
                                                      reflectionProvider: ReflectionProvider.Aux[u.type],
                                                      logger: TrivialLogger,
                                                    )(targetType: c.Type): c.Expr[ProviderMagnet[T]] = {
      if (!reflectionProvider.isConcrete(targetType)) {
        c.abort(c.enclosingPosition,
          s"""Tried to derive constructor function for class $targetType, but the class is an
             |abstract class or a trait! Only concrete classes (`class` keyword) are supported""".stripMargin)
      } else {
        val associations = reflectionProvider.constructorParameterLists(targetType)

        generateProvider[T](
          parameters = associations,
          fun = args => q"new $targetType(...$args)",
          isGenerated = true
        )
      }
    }

    def mkAnyConstructorFunction(
                                  reflectionProvider: ReflectionProvider.Aux[u.type],
                                  logger: TrivialLogger,
                                )
                                (targetType: c.Type): (List[u.Association.Parameter], c.Tree) = {
      val tpe = ReflectionUtil.norm(u.u)(targetType)

      if (reflectionProvider.isConcrete(tpe)) {
        mkClassConstructorFunction(reflectionProvider, logger)(tpe)
      } else if (reflectionProvider.isWireableAbstract(tpe)) {
        mkTraitConstructorFunction(reflectionProvider, logger)(tpe)
      } else {
        c.abort(
          c.enclosingPosition,
          s"""AnyConstructor failure: couldn't generate a constructor for $tpe!
             |It's neither a concrete class nor a trait!""".stripMargin
        )
      }
    }

    def mkTraitConstructorFunction(
                                    reflectionProvider: ReflectionProvider.Aux[u.type],
                                    logger: TrivialLogger,
                                  )
                                  (targetType: c.Type): (List[u.Association.Parameter], c.Tree) = {
      import u.Wiring.SingletonWiring.Trait

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

      val Trait(_, wireables, prefix@_) = reflectionProvider.symbolToWiring(targetType)
      val (traitAssociations, traitCtorArgs, wireMethods) = wireables.map(mkTraitMethod(_)).unzip3
      val (ctorAssociations, classCtorArgs, ctorParams) = mkClassConstructorFunctionImpl(reflectionProvider, logger)(targetType)

      val ctorLambda = newTraitWithMethods(targetType, ctorParams, wireMethods)
      val constructor = q"(..${classCtorArgs ++ traitCtorArgs}) => _root_.izumi.distage.constructors.TraitConstructor.wrapInitialization[$targetType]($ctorLambda)"

      (ctorAssociations ++ traitAssociations, constructor)
    }

    def newTraitWithMethods(
                             targetType: Type,
                             arguments: List[List[TermName]],
                             methods: List[Tree],
                           ): Tree = {
      val parents = ReflectionUtil.intersectionTypeMembers[u.u.type](targetType)
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

    def mkClassConstructorFunction(
                                    reflectionProvider: ReflectionProvider.Aux[u.type],
                                    logger: TrivialLogger,
                                  )
                                  (targetType: c.Type): (List[u.Association.Parameter], c.Tree) = {
      if (!reflectionProvider.isConcrete(targetType)) {
        c.abort(c.enclosingPosition,
          s"""Tried to derive constructor function for class $targetType, but the class is an
             |abstract class or a trait! Only concrete classes (`class` keyword) are supported""".stripMargin)
      } else {
        val (associations, ctorArgs, ctorArgNamesLists) = mkClassConstructorFunctionImpl(reflectionProvider, logger)(targetType)
        (associations, q"(..$ctorArgs) => new $targetType(...$ctorArgNamesLists)")
      }
    }

    def mkClassConstructorFunctionImpl(
                                        reflectionProvider: ReflectionProvider.Aux[u.type],
                                        logger: TrivialLogger,
                                      )
                                      (targetType: c.Type): (List[u.Association.Parameter], List[c.Tree], List[List[TermName]]) = {
      val paramLists = reflectionProvider.constructorParameterLists(targetType)
      val fnArgsNamesLists = paramLists.map(_.map(mkCtorArgument(_)))

      val (associations, ctorArgs) = fnArgsNamesLists.flatten.map {
        case CtorArgument(p, a, _) => (p, a)
      }.unzip
      val ctorArgNamesLists = fnArgsNamesLists.map(_.map(_.ctorArgumentName))

      (associations, ctorArgs, ctorArgNamesLists)
    }

    def generateProvider[T: c.WeakTypeTag](
                                            parameters: List[List[u.Association.Parameter]],
                                            fun: List[List[c.Tree]] => c.Tree,
                                            isGenerated: Boolean,
                                          ): c.Expr[ProviderMagnet[T]] = {
      val tools = DIUniverseLiftables(u)

      import tools.{liftTypeToSafeType, liftableParameter}
      import u.Association

      var i = 0
      val res = parameters.map(_.map {
        param =>
          val strippedByNameTpe = param.copy(symbol = param.symbol.withTpe {
            ReflectionUtil.stripByName(u.u)(param.symbol.finalResultType)
          })
          val seqCast = if (param.isByName) {
            q"seqAny($i).asInstanceOf[() => ${param.tpe}]()"
          } else {
            q"seqAny($i).asInstanceOf[${param.tpe}]"
          }
          i += 1
          strippedByNameTpe -> seqCast
      })
      val (substitutedByNames, casts) = (res.flatten.map(_._1), res.map(_.map(_._2)))

      val parametersNoByName = Liftable.liftList[Association.Parameter].apply(substitutedByNames)

      c.Expr[ProviderMagnet[T]] {
        q"""{
        new ${weakTypeOf[ProviderMagnet[T]]}(
          new ${weakTypeOf[RuntimeDIUniverse.Provider.ProviderImpl[T]]}(
            $parametersNoByName,
            ${liftTypeToSafeType(weakTypeOf[T])},
            { seqAny => ${fun(casts)} },
            $isGenerated,
          )
        )
      }"""
      }
    }
  }
  object ConstructorMacros {
    type Aux[C <: blackbox.Context, U <: StaticDIUniverse] = ConstructorMacros {val c: C; val u: U}

    def apply(c0: blackbox.Context)(u0: StaticDIUniverse.Aux[c0.universe.type]): ConstructorMacros.Aux[c0.type, u0.type] = {
      new ConstructorMacros {
        val c: c0.type = c0;
        val u: u0.type = u0
      }
    }
  }

}
