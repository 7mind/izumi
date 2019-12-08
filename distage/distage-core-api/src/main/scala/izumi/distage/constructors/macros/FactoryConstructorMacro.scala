package izumi.distage.constructors.macros

import izumi.distage.constructors.{AnyConstructor, DebugProperties, FactoryConstructor}
import izumi.distage.model.providers.ProviderMagnet
import izumi.distage.model.provisioning.proxies.ProxyDispatcher.ByNameWrapper
import izumi.distage.model.provisioning.strategies.FactoryExecutor
import izumi.distage.model.reflection.macros.{DIUniverseLiftables, ProviderMagnetMacro0}
import izumi.distage.model.reflection.universe.{RuntimeDIUniverse, StaticDIUniverse}
import izumi.distage.reflection.ReflectionProviderDefaultImpl
import izumi.fundamentals.reflection.{ReflectionUtil, TrivialMacroLogger}

import scala.annotation.tailrec
import scala.reflect.macros.blackbox

object FactoryConstructorMacro {

  def mkFactoryConstructor[T: c.WeakTypeTag](c: blackbox.Context): c.Expr[FactoryConstructor[T]] = {
    import c.universe._

    val macroUniverse = StaticDIUniverse(c)

    val reflectionProvider = ReflectionProviderDefaultImpl(macroUniverse)
    val logger = TrivialMacroLogger.make[this.type](c, DebugProperties.`izumi.debug.macro.distage.constructors`)

    // A hack to support generic methods inside factories. No viable type info is available for generic parameters of these methods
    // so we have to resort to WeakTypeTags and thread this ugly fucking `if` everywhere ;_;
    val tools = DIUniverseLiftables.generateUnsafeWeakSafeTypes(macroUniverse)

    import macroUniverse.Wiring._
    import macroUniverse._
    import tools.{liftableBasicDIKey, liftableSymbolInfo}

    def _unsafeWrong_convertReflectiveWiringToFunctionWiring(w: Wiring.SingletonWiring.ReflectiveInstantiationWiring): Tree = {
      w.instanceType.use {
        tpe =>
          q"""{
            ${symbolOf[AnyConstructor.type].asClass.module}.generateUnsafeWeakSafeTypes[$tpe].provider.get
          }"""
      }
    }

    val targetType = ReflectionUtil.norm(c.universe: c.universe.type)(weakTypeOf[T])

    val factory@Factory(unsafeRet, factoryMethods, traitDependencies) = reflectionProvider.symbolToWiring(targetType)
    val (dependencyAssociations0, dependencyArgs0, dependencyMethods) = TraitConstructorMacro.mkTraitArgMethods(c)(macroUniverse)(logger, traitDependencies)
    val ((dependencyAssociations1, dependencyArgs1), dependencyValsNames) = {
      val (x, y) = factory.factorySuppliedProductDeps.map {
        association => ConcreteConstructorMacro.paramToNamTreeEtc(c)(macroUniverse)(association.asParameter)
      }.unzip
      (x.unzip, y.map(termName => q"$termName"))
    }
    val (dependencyAssociations, dependencyArgs, dependencyHandles) =
      (dependencyAssociations0 ++ dependencyAssociations1,
        dependencyArgs0 ++ dependencyArgs1,
        dependencyAssociations0.map(parameter => q"${TermName(parameter.symbol.name)}") ++ dependencyValsNames,
      )

    val bynameWrapper = symbolOf[ByNameWrapper.type].asClass.module

    val producerMethods = factoryMethods.map {
      case Factory.FactoryMethod(factoryMethod, productConstructor, _) =>

        val (methodArgLists, methodArgs0) = {
          @tailrec def instantiatedMethod(tpe: Type): MethodTypeApi = tpe match {
            case m: MethodTypeApi => m
            case p: PolyTypeApi => instantiatedMethod(p.resultType)
          }
          val paramLists = instantiatedMethod(factoryMethod.underlying.typeSignatureIn(targetType)).paramLists.map(_.map {
            argSymbol =>
              val tpe = argSymbol.typeSignature
              val name = argSymbol.asTerm.name
              val expr = if (ReflectionUtil.isByName(u)(tpe)) {
                q"{ $bynameWrapper.apply($name) }"
              } else {
                q"{ $name }"
              }
              q"val $name: $tpe" -> (tpe -> expr)
          })
          paramLists.map(_.map(_._1)) -> paramLists.flatten.map(_._2)
        }

        val typeParams: List[TypeDef] = factoryMethod.underlying.asMethod.typeParams.map(symbol => c.internal.typeDef(symbol))

        val args = productConstructor.associations.map {
          param =>
            Option(dependencyAssociations.indexWhere(_.key == param.key)).filter(_ != -1).map(dependencyHandles(_))
              .orElse(methodArgs0.collectFirst {
                case (tpe, tree) if ReflectionUtil.stripByName(u)(tpe) =:= ReflectionUtil.stripByName(u)(param.tpe.use(identity)) =>
                  tree
              })
              .getOrElse(c.abort(c.enclosingPosition,
                s"Couldn't find anything for ${param.tpe.use(identity)} in ${dependencyAssociations.map(_.tpe.use(identity))} ++ ${methodArgs0.map(_._1)}"
              ))
        }.toList

        factoryMethod.finalResultType.use {
          resultTypeOfMethod =>
            q"""
            final def ${TermName(factoryMethod.name)}[..$typeParams](...$methodArgLists): $resultTypeOfMethod = {
              val executorArgs: ${typeOf[List[Any]]} = $args
              val wiring = ${_unsafeWrong_convertReflectiveWiringToFunctionWiring(productConstructor)}
              wiring.fun(executorArgs).asInstanceOf[$resultTypeOfMethod]
            }
            """
        }
    }

    val allMethods = producerMethods ++ dependencyMethods

    val parents = ReflectionUtil.intersectionTypeMembers[c.universe.type](targetType)
    val instantiate = if (allMethods.isEmpty) {
      q"new ..$parents {}"
    } else {
      q"new ..$parents { ..$allMethods }"
    }

    val constructor = q"(..$dependencyArgs) => _root_.izumi.distage.constructors.TraitConstructor.wrapInitialization(${tools.liftableSafeType(unsafeRet)})($instantiate): $targetType"

    val provided: c.Expr[ProviderMagnet[T]] = {
      val providerMagnetMacro = new ProviderMagnetMacro0[c.type](c)
      providerMagnetMacro.generateProvider[T](
        parameters = dependencyAssociations.asInstanceOf[List[providerMagnetMacro.macroUniverse.Association.Parameter]],
        fun = constructor,
        generateUnsafeWeakSafeTypes = false,
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

}
