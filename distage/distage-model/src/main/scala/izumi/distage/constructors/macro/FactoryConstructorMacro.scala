package izumi.distage.constructors.`macro`

import izumi.distage.constructors.{AnyConstructor, DebugProperties, FactoryConstructor}
import izumi.distage.model.providers.ProviderMagnet
import izumi.distage.model.provisioning.strategies.FactoryExecutor
import izumi.distage.model.reflection.macros.{DIUniverseLiftables, ProviderMagnetMacro0}
import izumi.distage.model.reflection.universe.{RuntimeDIUniverse, StaticDIUniverse}
import izumi.distage.provisioning.FactoryTools
import izumi.distage.reflection.ReflectionProviderDefaultImpl
import izumi.fundamentals.reflection.{AnnotationTools, ReflectionUtil, TrivialMacroLogger}

import scala.reflect.macros.blackbox

object FactoryConstructorMacro {

  def mkFactoryConstructor[T: c.WeakTypeTag](c: blackbox.Context): c.Expr[FactoryConstructor[T]] = {
    import c.universe._

    val macroUniverse = StaticDIUniverse(c)

    val reflectionProvider = ReflectionProviderDefaultImpl.Static(macroUniverse)
    val logger = TrivialMacroLogger.make[this.type](c, DebugProperties.`izumi.debug.macro.distage.constructors`)

    // A hack to support generic methods inside factories. No viable type info is available for generic parameters of these methods
    // so we have to resort to WeakTypeTags and thread this ugly fucking `if` everywhere ;_;
    val tools = DIUniverseLiftables.generateUnsafeWeakSafeTypes(macroUniverse)

    import macroUniverse.Association._
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

    val Factory(_, wireables, dependencies) = {
      reflectionProvider.symbolToWiring(targetType)
    }

    val (dependencyAssociations, dependencyArgs, dependencyMethods) = dependencies.map {
      case m @ AbstractMethod(symbol, key) =>
        key.tpe.use { tpe =>
          val methodName: TermName = TermName(symbol.name)
          val argName: TermName = c.freshName(methodName)

          (m.asParameter, q"val $argName: $tpe", q"override val $methodName: $tpe = $argName")
        }
    }.unzip3

    val (executorName, executorType) = TermName(c.freshName("executor")) -> typeOf[FactoryExecutor]
    val factoryTools = symbolOf[FactoryTools.type].asClass.module

    val (producerMethods, withContexts) = wireables.zipWithIndex.map {
      case (method@Factory.FactoryMethod(factoryMethod, productConstructor, methodArguments), methodIndex) =>

        val (methodArgs, executorArgs) = methodArguments.map {
          diKey =>
            val name = TermName(c.freshName())
            diKey.tpe.use {
              tpe =>
                q"$name: $tpe" -> q"{ $name }"
            }
        }.unzip

        val typeParams: List[TypeDef] = factoryMethod.underlying.asMethod.typeParams.map(symbol => c.internal.typeDef(symbol))

        val methodDef = factoryMethod.finalResultType.use(resultTypeOfMethod =>
          q"""
          override def ${TermName(factoryMethod.name)}[..$typeParams](..$methodArgs): $resultTypeOfMethod = {
            val executorArgs: ${typeOf[List[Any]]} = ${executorArgs.toList}

            $factoryTools.interpret($executorName.execute($methodIndex, executorArgs)).asInstanceOf[$resultTypeOfMethod]
          }
          """)

        val providedKeys = method.associationsFromContext.map(_.key)

        val methodInfo =
          q"""{
          val wiring = ${_unsafeWrong_convertReflectiveWiringToFunctionWiring(productConstructor)}

          new ${weakTypeOf[RuntimeDIUniverse.Wiring.FactoryFunction.FactoryMethod]}(
            ${liftableSymbolInfo(factoryMethod)},
            wiring,
            wiring.associations.map(_.key) diff ${Liftable.liftList(liftableBasicDIKey)(providedKeys.toList)} // work hard to ensure pointer equality of dikeys...
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

    val constructor = q"(..$allArgs) => ($instantiate): $targetType"

    val provided = {
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
          val withContexts = ${withContexts.toList}
          val ctxMap = withContexts.zipWithIndex.map(_.swap).toMap

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
