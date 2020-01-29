package izumi.distage.constructors.macros

import izumi.distage.constructors.{DebugProperties, FactoryConstructor}
import izumi.distage.model.providers.ProviderMagnet
import izumi.distage.model.reflection.ReflectionProvider
import izumi.distage.model.reflection.macros.ProviderMagnetMacro0
import izumi.distage.model.reflection.universe.StaticDIUniverse
import izumi.distage.reflection.ReflectionProviderDefaultImpl
import izumi.fundamentals.platform.console.TrivialLogger
import izumi.fundamentals.reflection.{ReflectionUtil, TrivialMacroLogger}

import scala.annotation.tailrec
import scala.reflect.macros.blackbox

object FactoryConstructorMacro {

  def mkFactoryConstructor[T: c.WeakTypeTag](c: blackbox.Context): c.Expr[FactoryConstructor[T]] = {
    import c.universe._

    val macroUniverse = StaticDIUniverse(c)
    val reflectionProvider = ReflectionProviderDefaultImpl(macroUniverse)
    val logger = TrivialMacroLogger.make[this.type](c, DebugProperties.`izumi.debug.macro.distage.constructors`)

    val targetType = ReflectionUtil.norm(c.universe: c.universe.type)(weakTypeOf[T].dealias)
    requireConcreteTypeConstructor(c)("FactoryConstructor", targetType)

    val uttils = ConstructorMacros(c)(macroUniverse)
    import uttils.{c => _, u => _, _}

    val factory = reflectionProvider.symbolToWiring(targetType) match {
      case factory: macroUniverse.Wiring.Factory => factory
      case wiring => c.abort(c.enclosingPosition,
        s"""Tried to create a `FactoryConstructor[$targetType]`, but `$targetType` is not a factory!
           |
           |Inferred wiring is: $wiring
           |""".stripMargin)
    }

    val traitMeta = factory.traitDependencies.map(mkCtorArgument(_))

    val ((dependencyAssociations, dependencyArgDecls, _), dependencyArgMap: Map[macroUniverse.DIKey.BasicKey, Tree]) = {
      val allMeta = {
        val paramMeta = factory.factoryProductDepsFromObjectGraph.map(mkCtorArgument(_))
        traitMeta ++ paramMeta
      }
      allMeta.unzip3 -> allMeta.map {
        case CtorArgument(param, _, argName) => param.key -> Liftable.liftName(argName)
      }.toMap
    }

    logger.log(
      s"""Got associations: $dependencyAssociations
         |Got argmap: $dependencyArgMap
         |""".stripMargin)

    val producerMethods = {
      factory.factoryMethods.map(generateFactoryMethod(c)(macroUniverse)(reflectionProvider)(logger)(uttils)(
        _,
        dependencyArgMap
      ))
    }

    val constructor = {
      val allMethods = producerMethods ++ traitMeta.map(_.traitMethodImpl)
      val instantiate = mkNewAbstractTypeInstanceExpr(targetType, Nil, allMethods)
      q"(..$dependencyArgDecls) => _root_.izumi.distage.constructors.TraitConstructor.wrapInitialization[$targetType]($instantiate)"
    }

    val provider: c.Expr[ProviderMagnet[T]] = {
      val providerMagnetMacro = new ProviderMagnetMacro0[c.type](c)
      providerMagnetMacro.generateProvider[T](
        parameters = dependencyAssociations.asInstanceOf[List[providerMagnetMacro.macroUniverse.Association.Parameter]],
        fun = constructor,
        isGenerated = true
      )
    }
    val res = c.Expr[FactoryConstructor[T]] {
      q"{ new ${weakTypeOf[FactoryConstructor[T]]}($provider) }"
    }
    logger.log(s"Final syntax tree of factory $targetType:\n$res")

    res
  }

  private def generateFactoryMethod[T: c.WeakTypeTag](c: blackbox.Context)
                                                     (macroUniverse: StaticDIUniverse.Aux[c.universe.type])
                                                     (reflectionProvider: ReflectionProvider.Aux[macroUniverse.type])
                                                     (logger: TrivialLogger)
                                                     (uttils: ConstructorMacros.Aux[c.type, macroUniverse.type])
                                                     (
                                                       factoryMethod0: macroUniverse.Wiring.Factory.FactoryMethod,
                                                       dependencyArgMap: Map[macroUniverse.DIKey.BasicKey, c.Tree],
                                                     ): c.Tree = {
    import c.universe._
    import macroUniverse.Wiring.Factory.FactoryMethod
    import macroUniverse.u
    import uttils.mkAnyConstructorFunction

    val FactoryMethod(factoryMethod, productConstructor, _) = factoryMethod0

    val (methodArgListDecls, methodArgList) = {
      @tailrec def instantiatedMethod(tpe: Type): MethodTypeApi = {
        tpe match {
          case m: MethodTypeApi => m
          case p: PolyTypeApi => instantiatedMethod(p.resultType)
        }
      }
      val paramLists = instantiatedMethod(factoryMethod.typeSignatureInDefiningClass).paramLists.map(_.map {
        argSymbol =>
          val tpe = argSymbol.typeSignature
          val name = argSymbol.asTerm.name
          q"val $name: $tpe" -> (tpe -> name)
      })
      paramLists.map(_.map(_._1)) -> paramLists.flatten.map(_._2)
    }

    val typeParams: List[TypeDef] = factoryMethod.underlying.asMethod.typeParams.map(c.internal.typeDef(_))

    val (associations, fnTree) = mkAnyConstructorFunction(reflectionProvider, logger)(productConstructor.instanceType)
    val args = associations.map {
      param =>
        val candidates = methodArgList.collect {
          case (tpe, termName) if ReflectionUtil.stripByName(u)(tpe) =:= ReflectionUtil.stripByName(u)(param.tpe) =>
            Liftable.liftName(termName)
        }
        candidates match {
          case one :: Nil =>
            one
          case Nil =>
            dependencyArgMap.getOrElse(param.key, c.abort(c.enclosingPosition,
              s"Couldn't find a dependency to satisfy parameter ${param.name}: ${param.tpe} in factoryArgs: ${dependencyArgMap.keys.map(_.tpe)}, methodArgs: ${methodArgList.map(_._1)}"
            ))
          case multiple =>
            multiple.find(_.toString == param.name)
              .getOrElse(c.abort(c.enclosingPosition,
                s"""Couldn't disambiguate between multiple arguments with the same type available for parameter ${param.name}: ${param.tpe} of ${factoryMethod.finalResultType} constructor
                   |Expected one of the arguments to be named `${param.name}` or for the type to be unique among factory method arguments""".stripMargin
              ))
        }
    }
    val freshName = TermName(c.freshName("wiring"))

    q"""
    final def ${TermName(factoryMethod.name)}[..$typeParams](...$methodArgListDecls): ${factoryMethod.finalResultType} = {
      val $freshName = $fnTree
      $freshName(..$args)
    }
    """
  }

}
