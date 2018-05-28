package com.github.pshirshov.izumi.distage.provisioning.strategies

import com.github.pshirshov.izumi.distage.model.definition.reflection.DIUniverseMacros
import com.github.pshirshov.izumi.distage.model.providers.ProviderMagnet
import com.github.pshirshov.izumi.distage.model.provisioning.strategies.FactoryExecutor
import com.github.pshirshov.izumi.distage.model.reflection.universe.{RuntimeDIUniverse, StaticDIUniverse}
import com.github.pshirshov.izumi.distage.provisioning.{FactoryConstructor, FactoryTools}
import com.github.pshirshov.izumi.distage.reflection.{DependencyKeyProviderDefaultImpl, ReflectionProviderDefaultImpl, SymbolIntrospectorDefaultImpl}
import com.github.pshirshov.izumi.fundamentals.reflection.{AnnotationTools, MacroUtil}

import scala.reflect.macros.blackbox

// TODO: Factories can exceed 22 arguments limit on function parameter list
object FactoryConstructorMacro {

  def mkFactoryConstructor[T: c.WeakTypeTag](c: blackbox.Context): c.Expr[FactoryConstructor[T]] = {
    import c.universe._

    val macroUniverse = StaticDIUniverse(c)

    val symbolIntrospector = SymbolIntrospectorDefaultImpl.Static(macroUniverse)
    val keyProvider = DependencyKeyProviderDefaultImpl.Static(macroUniverse)(symbolIntrospector)
    val reflectionProvider = ReflectionProviderDefaultImpl.Static(macroUniverse)(keyProvider, symbolIntrospector)
    val logger = MacroUtil.mkLogger[this.type](c)
    val tools = DIUniverseMacros(macroUniverse)

    import tools.{liftableRuntimeUniverse, liftableProductWiring, liftableSymbolInfo, liftableDIKey}
    import macroUniverse.Association._
    import macroUniverse.Wiring._
    import macroUniverse._

    val targetType = weakTypeOf[T]

    val FactoryMethod(_, wireables, dependencies) = reflectionProvider.symbolToWiring(
      SafeType(targetType)
    )

    val (dependencyArgs, dependencyMethods) = dependencies.map {
      case AbstractMethod(ctx, name, _, key) =>
        val tpe = key.tpe.tpe
        val methodName: TermName = TermName(name)
        val argName: TermName = c.freshName(methodName)

        val mods = AnnotationTools.mkModifiers(u)(ctx.methodSymbol.annotations)

        (q"$mods val $argName: $tpe", q"override val $methodName: $tpe = $argName")
    }.unzip

    val (executorName, executorType) = TermName(c.freshName("executor")) -> typeOf[FactoryExecutor].typeSymbol
    val factoryTools = symbolOf[FactoryTools.type].asClass.module

    // FIXME we can't remove runtime dependency on scala-reflect right now because:
    //  1. provisioner depends on RuntimeUniverse scala-reflect Types
    //  2. we need to lift DIKey & SafeType types (by calling RuntimeUniverse reflection)
    //
    //  Solution:
    //    * Provisioning shouldn't call reflection, all necessary info should be collected at binding
    //    * Universe types(DIKey, SafeType) should be interfaces not directly referencing scala-reflect types
    //    * API builder methods shouldn't require TypeTag
    //    * make scala-reflect dependency (% Provided) for distage-static
    //      * provided dependencies seem to be correctly transitive, at least shapeless doesn't require the user to
    //        to add provided scala-reflect to his .sbt
    val (producerMethods, withContexts) = wireables.zipWithIndex.map {
      case (method@FactoryMethod.WithContext(factoryMethod, productConstructor, methodArguments), methodIndex) =>

        val (methodArgs, executorArgs) = methodArguments.map {
          dIKey =>
            val name = TermName(c.freshName())
            q"$name: ${dIKey.tpe.tpe}" -> q"{ $name }"
        }.unzip

        val typeParams: List[TypeDef] = factoryMethod.underlying.asMethod.typeParams.map(symbol => c.internal.typeDef(symbol))

        val resultTypeOfMethod: Type = factoryMethod.finalResultType.tpe

        val methodDef =  q"""
          override def ${TermName(factoryMethod.name)}[..$typeParams](..$methodArgs): $resultTypeOfMethod = {
            val executorArgs: ${typeOf[List[Any]]} = ${executorArgs.toList}

            $factoryTools.interpret($executorName.execute($methodIndex, executorArgs)).asInstanceOf[$resultTypeOfMethod]
          }
          """


        val providedKeys = method.associationsFromContext.map(_.wireWith)

        val methodInfo =q"""{
          val wiring = ${productConstructor.asInstanceOf[Wiring.UnaryWiring.ProductWiring]}

          $RuntimeDIUniverse.Wiring.FactoryFunction.WithContext(
            ${factoryMethod: SymbolInfo}
            , wiring
            , wiring.associations.map(_.wireWith) diff ${providedKeys.toList} // work hard to ensure pointer equality of dikeys...
          )
        }"""

        methodDef -> methodInfo
    }.unzip

    val executorArg = q"$executorName: $executorType"
    val allArgs = executorArg +: dependencyArgs
    val allMethods = producerMethods ++ dependencyMethods
    val instantiate = if (allMethods.isEmpty) {
      q"new $targetType {}"
    } else {
      q"new $targetType { ..$allMethods }"
    }

    val defConstructor =
      q"""
      def constructor(..$allArgs): $targetType =
        ($instantiate).asInstanceOf[$targetType]
      """

    val providerMagnet = symbolOf[ProviderMagnet.type].asClass.module
    val res = c.Expr[FactoryConstructor[T]] {
      q"""
          {
          $defConstructor

          val withContexts = ${withContexts.toList}
          val ctxMap = withContexts.zipWithIndex.map(_.swap).toMap

          val magnetized = $providerMagnet.apply[$targetType](constructor _)
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
