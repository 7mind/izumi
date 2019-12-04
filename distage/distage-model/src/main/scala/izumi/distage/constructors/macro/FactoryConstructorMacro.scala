package izumi.distage.constructors.`macro`

import izumi.distage.constructors.{AnyConstructor, DebugProperties, FactoryConstructor}
import izumi.distage.model.providers.ProviderMagnet
import izumi.distage.model.provisioning.strategies.FactoryExecutor
import izumi.distage.model.provisioning.strategies.ProxyDispatcher.ByNameWrapper
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
            val fun = ${symbolOf[AnyConstructor.type].asClass.module}.generateUnsafeWeakSafeTypes[$tpe].provider.get
            ${reify(RuntimeDIUniverse.Wiring.SingletonWiring.Function)}.apply(fun, fun.associations)
          }"""
      }
    }

    val targetType = ReflectionUtil.norm(c.universe: c.universe.type)(weakTypeOf[T])

    val Factory(unsafeRet, factoryMethods, traitDependencies) = reflectionProvider.symbolToWiring(targetType)
    val (dependencyAssociations, dependencyArgs, dependencyMethods) = TraitConstructorMacro.mkTraitArgMethods(c)(macroUniverse)(logger, traitDependencies)

    val (executorName, executorType) = TermName(c.freshName("executor")) -> typeOf[FactoryExecutor]
    val bynameWrapper = symbolOf[ByNameWrapper.type].asClass.module

    val (producerMethods, withContexts) = factoryMethods.zipWithIndex.map {
      case (method@Factory.FactoryMethod(factoryMethod, productConstructor, _), methodIndex) =>

        val (methodArgLists, executorArgs) = {
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
              q"val $name: $tpe" -> expr
          })
          paramLists.map(_.map(_._1)) -> paramLists.flatten.map(_._2)
        }

        val typeParams: List[TypeDef] = factoryMethod.underlying.asMethod.typeParams.map(symbol => c.internal.typeDef(symbol))

        val methodDef = factoryMethod.finalResultType.use {
          resultTypeOfMethod =>
            q"""
            final def ${TermName(factoryMethod.name)}[..$typeParams](...$methodArgLists): $resultTypeOfMethod = {
              val executorArgs: ${typeOf[List[Any]]} = ${executorArgs.toList}

              $executorName.execute($methodIndex, executorArgs).asInstanceOf[$resultTypeOfMethod]
            }
            """
        }

        val providedKeys = method.associationsFromContext.map(_.key)

        val methodInfo =
          q"""{
          val wiring = ${_unsafeWrong_convertReflectiveWiringToFunctionWiring(productConstructor)}

          new ${weakTypeOf[RuntimeDIUniverse.Wiring.FactoryFunction.FactoryMethod]}(
            ${liftableSymbolInfo(factoryMethod)},
            wiring,
            wiring.associations.map(_.key) diff ${Liftable.liftList(liftableBasicDIKey)(providedKeys.toList)}
          )
        }"""

        methodDef -> methodInfo
    }.unzip

    val allMethods = producerMethods ++ dependencyMethods

    val parents = ReflectionUtil.intersectionTypeMembers[c.universe.type](targetType)
    val instantiate = if (allMethods.isEmpty) {
      q"new ..$parents {}"
    } else {
      q"new ..$parents { ..$allMethods }"
    }

    val executorArg = q"val $executorName: $executorType"
    val allArgs = executorArg +: dependencyArgs
    val executorAssociation = {
      val unsafeSafeType = SafeType(executorType)
      val executorKey = DIKey.TypeKey(unsafeSafeType)
      Association.Parameter(SymbolInfo.Static("executor", unsafeSafeType, Nil, false, false), executorKey)
    }
    val allAssociations = executorAssociation +: dependencyAssociations

    val constructor = q"(..$allArgs) => _root_.izumi.distage.constructors.TraitConstructor.wrapInitialization(${tools.liftableSafeType(unsafeRet)})($instantiate): $targetType"

    val provided: c.Expr[ProviderMagnet[T]] = {
      val providerMagnetMacro = new ProviderMagnetMacro0[c.type](c)
      providerMagnetMacro.generateProvider[T](
        allAssociations.asInstanceOf[List[providerMagnetMacro.macroUniverse.Association.Parameter]],
        constructor,
        generateUnsafeWeakSafeTypes = false,
      )
    }
    val res = c.Expr[FactoryConstructor[T]] {
      q"""
          {
          val ctxMap = $withContexts.zipWithIndex.map(_.swap).toMap

          val magnetized = $provided
          val res = new ${weakTypeOf[ProviderMagnet[T]]}(
            new ${weakTypeOf[RuntimeDIUniverse.Provider.FactoryProvider.FactoryProviderImpl]}(magnetized.get, ctxMap)
          )

          new ${weakTypeOf[FactoryConstructor[T]]}(res)
          }
       """
    }
    logger.log(s"Final syntax tree of factory $targetType:\n$res")

    res
  }

}
