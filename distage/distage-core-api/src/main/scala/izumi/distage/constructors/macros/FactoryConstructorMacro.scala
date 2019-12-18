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

    import macroUniverse.Wiring._
    import macroUniverse._

    val targetType = ReflectionUtil.norm(c.universe: c.universe.type)(weakTypeOf[T])

    val factory@Factory(factoryMethods, _) = reflectionProvider.symbolToWiring(targetType)
    val traitMeta = factory.traitDependencies.map(TraitConstructorMacro.mkArgFromAssociation(c)(macroUniverse)(logger)(_))
    val paramMeta = factory.factoryProductDepsFromObjectGraph.map(TraitConstructorMacro.mkArgFromAssociation(c)(macroUniverse)(logger)(_))
    val allMeta = traitMeta ++ paramMeta
    val (dependencyAssociations, dependencyArgDecls, _) = allMeta.unzip3
    val dependencyMethods = traitMeta.map(_._3._1)
    val dependencyArgMap: Map[DIKey.BasicKey, TermName] = allMeta.map { case (param, _, (_, argName)) => param.key -> argName }.toMap

    logger.log(
      s"""Got associations: $dependencyAssociations
         |Got argmap: $dependencyArgMap
         |""".stripMargin)

    val producerMethods = factoryMethods.map {
      case Factory.FactoryMethod(factoryMethod, productConstructor, _) =>

        val (methodArgListDecls, methodArgsMap) = {
          @tailrec def instantiatedMethod(tpe: Type): MethodTypeApi = tpe match {
            case m: MethodTypeApi => m
            case p: PolyTypeApi => instantiatedMethod(p.resultType)
          }

          val paramLists = instantiatedMethod(factoryMethod.underlying.typeSignatureIn(targetType)).paramLists.map(_.map {
            argSymbol =>
              val tpe = argSymbol.typeSignature
              val name = argSymbol.asTerm.name
              q"val $name: $tpe" -> (tpe -> name)
          })
          paramLists.map(_.map(_._1)) -> paramLists.flatten.map(_._2)
        }

        val typeParams: List[TypeDef] = factoryMethod.underlying.asMethod.typeParams.map(symbol => c.internal.typeDef(symbol))

        val (associations, fnTree) = mkAnyProductConstructorUnwrapped(c)(macroUniverse)(reflectionProvider, logger)(productConstructor.instanceType)
        val args = associations.map {
          param =>
            dependencyArgMap
              .get(param.key)
              .orElse {
                methodArgsMap.collectFirst {
                  case (tpe, tree) if ReflectionUtil.stripByName(u)(tpe) =:= ReflectionUtil.stripByName(u)(param.tpe) =>
                    tree
                }
              }
              .getOrElse {
                c.abort(c.enclosingPosition,
                  s"Couldn't find anything for ${param.tpe} in ${dependencyAssociations.map(_.tpe)} ++ ${methodArgsMap.map(_._1)}"
                )
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

    val allMethods = producerMethods ++ dependencyMethods

    val instantiate = TraitConstructorMacro.newWithMethods(c)(targetType, allMethods)

    val constructor = q"(..$dependencyArgDecls) => _root_.izumi.distage.constructors.TraitConstructor.wrapInitialization[$targetType]($instantiate)"

    val provided: c.Expr[ProviderMagnet[T]] = {
      val providerMagnetMacro = new ProviderMagnetMacro0[c.type](c)
      providerMagnetMacro.generateProvider[T](
        parameters = dependencyAssociations.asInstanceOf[List[providerMagnetMacro.macroUniverse.Association.Parameter]],
        fun = constructor,
        isGenerated = true
      )
    }
    val res = c.Expr[FactoryConstructor[T]] {
      q"""
          {
          new ${weakTypeOf[FactoryConstructor[T]]}($provided)
          }
       """
    }
    logger.log(s"Final syntax tree of factory $targetType:\n$res")

    res
  }

  def mkAnyProductConstructorUnwrapped(c: blackbox.Context)
                                      (macroUniverse: StaticDIUniverse.Aux[c.universe.type])
                                      (reflectionProvider: ReflectionProvider.Aux[macroUniverse.type],
                                       logger: TrivialLogger)
                                      (targetType: c.Type): (List[macroUniverse.Association.Parameter], c.Tree) = {

    val tpe = ReflectionUtil.norm(c.universe: c.universe.type)(targetType)

    if (reflectionProvider.isConcrete(tpe)) {
      ConcreteConstructorMacro.mkConcreteConstructorUnwrappedImpl(c)(macroUniverse)(reflectionProvider, logger)(tpe)
    } else if (reflectionProvider.isWireableAbstract(tpe)) {
      TraitConstructorMacro.mkTraitConstructorUnwrappedImpl(c)(macroUniverse)(reflectionProvider, logger)(tpe)
    } else {
      c.abort(
        c.enclosingPosition,
        s"""AnyConstructor failure: couldn't generate a constructor for $tpe!
           |It's neither a concrete class nor a trait!""".stripMargin
      )
    }
  }

}
