package izumi.distage.constructors

import izumi.distage.model.providers.ProviderMagnet
import izumi.distage.model.reflection.ReflectionProvider
import izumi.distage.model.reflection.macros.DIUniverseLiftables
import izumi.distage.model.reflection.universe.RuntimeDIUniverse.Wiring
import izumi.distage.model.reflection.universe.{RuntimeDIUniverse, StaticDIUniverse}
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
      val (argName, freshArgName) = association.ctorArgumentExpr(c)
      CtorArgument(association.asParameter, argName, freshArgName)
    }

    case class CtorArgument(parameter: u.Association.Parameter, ctorArgument: Tree, ctorArgumentName: Tree) {
      def traitMethodExpr: Tree = parameter.traitMethodExpr(ctorArgumentName)
    }
    object CtorArgument {
      implicit def unzip3: CtorArgument => (u.Association.Parameter, Tree, Tree) = {
        case CtorArgument(parameter, ctorArgument, ctorArgumentName) => (parameter, ctorArgument, ctorArgumentName)
      }

      def unzipLists(ls: List[List[CtorArgument]]): (List[u.Association.Parameter], List[Tree], List[List[Tree]]) = {
        val (associations, ctorArgs) = ls.flatten.map {
          case CtorArgument(p, a, _) => (p, a)
        }.unzip
        val ctorArgNamesLists = ls.map(_.map(_.ctorArgumentName))
        (associations, ctorArgs, ctorArgNamesLists)
      }
    }

    def mkTraitMethod(method: u.Association.AbstractMethod): TraitMethod = {
      val (argExpr, freshArgName) = method.ctorArgumentExpr(c)
      TraitMethod(method.asParameter, argExpr, method.traitMethodExpr(freshArgName))
    }

    case class TraitMethod(parameter: u.Association.Parameter, ctorArgument: Tree, traitMethodImpl: Tree)
    object TraitMethod {
      implicit def unzip3: TraitMethod => (u.Association.Parameter, Tree, Tree) = {
        case TraitMethod(parameter, ctorArgument, traitMethodImpl) => (parameter, ctorArgument, traitMethodImpl)
      }
    }

    def mkClassConstructorProvider[T: c.WeakTypeTag](reflectionProvider: ReflectionProvider.Aux[u.type])(targetType: Type): c.Expr[ProviderMagnet[T]] = {
      if (!reflectionProvider.isConcrete(targetType)) {
        c.abort(c.enclosingPosition,
          s"""Tried to derive constructor function for class $targetType, but the class is an
             |abstract class or a trait! Only concrete classes (`class` keyword) are supported""".stripMargin)
      }

      val associations = reflectionProvider.constructorParameterLists(targetType)
      generateProvider[T](
        parameters = associations,
        fun = args => q"new $targetType(...$args)",
      )
    }

    final case class FunctionCtor(parameters: List[u.Association.Parameter], ctorArguments: List[Tree], ctorParameters: List[List[Tree]])
    final case class ProviderCtor(parameters: List[List[u.Association.Parameter]], newExpr: List[List[Tree]] => Tree)

    def mkAnyConstructorFunction(wiring: u.Wiring.SingletonWiring): (List[u.Association.Parameter], Tree) = {
      wiring match {
        case w: u.Wiring.SingletonWiring.Class =>
          mkClassConstructorFunction(w)
        case w: u.Wiring.SingletonWiring.Trait =>
          mkTraitConstructorFunction(w)
      }
    }
    def mkClassConstructorFunction(w: u.Wiring.SingletonWiring.Class): (List[u.Association.Parameter], Tree) = {
      val u.Wiring.SingletonWiring.Class(targetType, classParameters, _) = w
      val (associations, ctorArgs, ctorArgNamesLists) = CtorArgument.unzipLists(classParameters.map(_.map(mkCtorArgument(_))))
      (associations, q"(..$ctorArgs) => new $targetType(...$ctorArgNamesLists)")
//      val argsNamess = classParameters.map(_.map(_.ctorArgumentExpr(c)))
//      q"(classParameters.flatten, ..${argsNamess.flatten.map(_._1)}) => new $targetType(...${argsNamess.map(_.map(_._2))})"
    }
    def mkTraitConstructorFunction(wiring: u.Wiring.SingletonWiring.Trait): (List[u.Association.Parameter], Tree) = {
      val u.Wiring.SingletonWiring.Trait(targetType, classParameters, methods, _) = wiring

      val (ctorAssociations, classCtorArgs, ctorParams) = CtorArgument.unzipLists(classParameters.map(_.map(mkCtorArgument(_))))
      val (traitAssociations, traitCtorArgs, wireMethods) = methods.map(mkTraitMethod(_)).unzip3

      (ctorAssociations ++ traitAssociations, {
        val newExpr = mkNewAbstractTypeInstanceApplyExpr(targetType, ctorParams, wireMethods)
        q"(..${classCtorArgs ++ traitCtorArgs}) => _root_.izumi.distage.constructors.TraitConstructor.wrapInitialization[$targetType]($newExpr)"
      })
    }

    def mkTraitConstructorProvider[T: c.WeakTypeTag](wiring: u.Wiring.SingletonWiring.Trait): c.Expr[ProviderMagnet[T]] = {
      val u.Wiring.SingletonWiring.Trait(targetType, classParameters, methods, _) = wiring

      val (ctorAssociations, classCtorArgs, ctorParams) = CtorArgument.unzipLists(classParameters.map(_.map(mkCtorArgument(_))))
      val (traitAssociations, traitCtorArgs, wireMethods) = methods.map(mkTraitMethod(_)).unzip3

      val newExpr = mkNewAbstractTypeInstanceApplyExpr(targetType, ctorParams, wireMethods)
      q"(..${classCtorArgs ++ traitCtorArgs}) => _root_.izumi.distage.constructors.TraitConstructor.wrapInitialization[$targetType]($newExpr)"
      generateProvider[T](
        classParameters :+ traitAssociations,
        argss => mkNewAbstractTypeInstanceApplyExpr(targetType, argss.init, argss.last)
      )
    }

    def traitConstructorAssertion(targetType: Type): Unit = {
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
    }

    def mkNewAbstractTypeInstanceApplyExpr(
                                            targetType: Type,
                                            constructorParameters: List[List[Tree]],
                                            methodImpls: List[Tree],
                                          ): Tree = {
      val parents = ReflectionUtil.intersectionTypeMembers[u.u.type](targetType)
      parents match {
        case parent :: Nil =>
          if (methodImpls.isEmpty) {
            q"new $parent(...$constructorParameters) {}"
          } else {
            q"new $parent(...$constructorParameters) { ..$methodImpls }"
          }
        case _ =>
          if (constructorParameters.nonEmpty) {
            c.abort(c.enclosingPosition,
              s"""Unsupported case: intersection type containing an abstract class.
                 |Please manually create an abstract class with the added traits.
                 |When trying to create a TraitConstructor for $targetType""".stripMargin)
          } else {
            if (methodImpls.isEmpty) {
              q"new ..$parents {}"
            } else {
              q"new ..$parents { ..$methodImpls }"
            }
          }
      }
    }

    def generateProvider[T: c.WeakTypeTag](
                                            parameters: List[List[u.Association.Parameter]],
                                            fun: List[List[Tree]] => Tree,
                                          ): c.Expr[ProviderMagnet[T]] = {
      val tools = DIUniverseLiftables(u)

      import tools.{liftTypeToSafeType, liftableParameter}

      val seqName = TermName(c.freshName("seqAny"))

      val casts = {
        var i = 0
        parameters.map(_.map {
          param =>
            val seqCast = if (param.isByName) {
              q"$seqName($i).asInstanceOf[() => ${param.tpe}].apply()"
            } else {
              q"$seqName($i).asInstanceOf[${param.tpe}]"
            }

            i += 1
            seqCast
        })
      }

      c.Expr[ProviderMagnet[T]] {
        q"""{
        new ${weakTypeOf[ProviderMagnet[T]]}(
          new ${weakTypeOf[RuntimeDIUniverse.Provider.ProviderImpl[T]]}(
            ${Liftable.liftList.apply(parameters.flatten)},
            ${liftTypeToSafeType(weakTypeOf[T])},
            { ($seqName: _root_.scala.Seq[_root_.scala.Any]) => ${fun(casts)} },
            true,
          )
        )
      }"""
      }
    }

    def symbolToFactory(reflectionProvider: ReflectionProvider.Aux[u.type])(targetType: Type): u.Wiring.Factory = {
      reflectionProvider.symbolToWiring(targetType) match {
        case factory: u.Wiring.Factory => factory
        case wiring => throw new RuntimeException(
          s"""Tried to create a `FactoryConstructor[$targetType]`, but `$targetType` is not a factory!
             |
             |Inferred wiring is: $wiring""".stripMargin)
      }
    }

    def symbolToTrait(reflectionProvider: ReflectionProvider.Aux[u.type])(targetType: Type): u.Wiring.SingletonWiring.Trait = {
      reflectionProvider.symbolToWiring(targetType) match {
        case trait0: u.Wiring.SingletonWiring.Trait => trait0
        case wiring => throw new RuntimeException(
          s"""Tried to create a `TraitConstructor[$targetType]`, but `$targetType` is not a trait or an abstract class!
             |
             |Inferred wiring is: $wiring""".stripMargin)
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
