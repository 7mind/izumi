package com.github.pshirshov.izumi.distage.provisioning.strategies

import com.github.pshirshov.izumi.distage.model.providers.ProviderMagnet
import com.github.pshirshov.izumi.distage.model.provisioning.strategies.FactoryExecutor
import com.github.pshirshov.izumi.distage.model.reflection.macros.{DIUniverseLiftables, TrivialMacroLogger}
import com.github.pshirshov.izumi.distage.model.reflection.universe.{RuntimeDIUniverse, StaticDIUniverse}
import com.github.pshirshov.izumi.distage.provisioning.{AnyConstructor, FactoryConstructor, FactoryTools}
import com.github.pshirshov.izumi.distage.reflection.{DependencyKeyProviderDefaultImpl, ReflectionProviderDefaultImpl, SymbolIntrospectorDefaultImpl}
import com.github.pshirshov.izumi.fundamentals.reflection.AnnotationTools

import scala.reflect.macros.blackbox

object FactoryConstructorMacro {

  def mkFactoryConstructor[T: c.WeakTypeTag](c: blackbox.Context): c.Expr[FactoryConstructor[T]] = {
    import c.universe._

    val macroUniverse = StaticDIUniverse(c)

    val symbolIntrospector = SymbolIntrospectorDefaultImpl.Static(macroUniverse)
    val keyProvider = DependencyKeyProviderDefaultImpl.Static(macroUniverse)(symbolIntrospector)
    val reflectionProvider = ReflectionProviderDefaultImpl.Static(macroUniverse)(keyProvider, symbolIntrospector)
    val logger = TrivialMacroLogger[this.type](c)

    // A hack to support generic methods inside factories. No viable type info is available for generic parameters of these methods
    // so we have to resort to WeakTypeTags and thread this ugly fucking `if` everywhere ;_;
    val tools = DIUniverseLiftables.generateUnsafeWeakSafeTypes(macroUniverse)

    import tools.{liftableRuntimeUniverse, liftableSymbolInfo, liftableBasicDIKey}
    import macroUniverse.Association._
    import macroUniverse.Wiring._
    import macroUniverse._

    val liftableProductWiring: Liftable[Wiring.UnaryWiring.ProductWiring] = {
      // TODO: Macro call in liftable that substitutes for a different type (not just in a different universe...)
      w: Wiring.UnaryWiring.ProductWiring =>
        q"""{
        val fun = ${symbolOf[AnyConstructor.type].asClass.module}.generateUnsafeWeakSafeTypes[${w.instanceType.tpe}].provider.get

        $RuntimeDIUniverse.Wiring.UnaryWiring.Function.apply(fun, fun.associations)
        }"""
    }

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

        // TODO: remove asInstanceOf[ProductWiring] by generating providers for classes too, so the only wiring allowed is Function
        val methodInfo =q"""{
          val wiring = ${liftableProductWiring(productConstructor.asInstanceOf[Wiring.UnaryWiring.ProductWiring])}

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
