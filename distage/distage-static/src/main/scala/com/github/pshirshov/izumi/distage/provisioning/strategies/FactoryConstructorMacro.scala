package com.github.pshirshov.izumi.distage.provisioning.strategies

import com.github.pshirshov.izumi.distage.model.definition.reflection.DIUniverseMacros
import com.github.pshirshov.izumi.distage.model.providers.ProviderMagnet
import com.github.pshirshov.izumi.distage.model.provisioning.FactoryExecutor
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

    import tools.{liftableProductWiring, liftableDIKey, liftableTypeKey, liftableRuntimeUniverse, liftableSymbolInfo}
    import macroUniverse.Association._
    import macroUniverse.Wiring._
    import macroUniverse._

    val targetType = weakTypeOf[T]

    val factoryInfo@FactoryMethod(_, wireables, dependencies) = reflectionProvider.symbolToWiring(
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
    //    * Provisioning shouldn't call reflection, all necessary info should be collected by planner
    //    * Universe types(DIKey, SafeType) should be interfaces not directly referencing scala-reflect types
    //    * API builder methods shouldn't require TypeTag
    //    * make scala-reflect dependency (% Provided) for distage-static
    //      * provided dependencies seem to be correctly transitive, at least shapeless doesn't require the user to
    //        to add provided scala-reflect to his .sbt
    val producerMethods = wireables.zipWithIndex.map {
      case (method@FactoryMethod.WithContext(factoryMethod, productConstructor, methodArguments), i) =>

        val (methodArgs, executorArgs) = methodArguments.map {
          dIKey =>
            val name = TermName(c.freshName())
            q"$name: ${dIKey.tpe.tpe}" -> q"{ $name }"
        }.unzip

        val typeParams: List[TypeDef] = factoryMethod.underlying.asMethod.typeParams.map(symbol => c.internal.typeDef(symbol))

        val resultTypeOfMethod: Type = factoryMethod.finalResultType.tpe
        val instanceType: TypeFull = productConstructor.instanceType

        val externalKeys = method.providedAssociations.map(_.wireWith).toList

        q"""
        override def ${TermName(factoryMethod.name)}[..$typeParams](..$methodArgs): $resultTypeOfMethod = {
          val wiring: ${typeOf[RuntimeDIUniverse.Wiring.UnaryWiring]} = withContexts($i).wireWith //ensure pointer equality

          val executorArgs: ${typeOf[Map[RuntimeDIUniverse.DIKey, Any]]} =
            ${if (executorArgs.nonEmpty)
              // zip directly instead of creating new keys to ensure pointer equality for typetags inside wiring and inside executableOps
              // to support weakTypeTag-based generics since they are pointer equality based
              {
                q"""{
                  val allAssociations = wiring.associations
                  val arguments: ${typeOf[List[Any]]} = ${executorArgs.toList}

                  val externalKeys: ${typeOf[Set[RuntimeDIUniverse.DIKey]]} = $externalKeys.toSet
                  val associations = allAssociations.filterNot(externalKeys contains _.wireWith)

                  if (associations.size != arguments.size) {
                    throw new _root_.com.github.pshirshov.izumi.distage.model.exceptions.DIException(
                      "Divergence between constructor arguments and dependencies: " + arguments + " vs " + associations, null)
                  }

                  associations.map(_.wireWith).zip(arguments).toMap
                }"""
              } else {
                q"{ ${Map.empty[Unit, Unit]} } "
              }
            }

          $factoryTools.interpret(
            $executorName.execute(
              executorArgs
              , $factoryTools.mkExecutableOp(
                  ${DIKey.TypeKey(instanceType)}
                  , wiring
                )
            )
          ).asInstanceOf[$resultTypeOfMethod]
        }
        """
    }

    val withContexts = wireables.map {
      case FactoryMethod.WithContext(factoryMethod, productConstructor, methodArguments) =>
        q"""
            val ctor = $productConstructor
           $RuntimeDIUniverse.Provider.FactoryProvider.FactoryProduct(${factoryMethod: SymbolInfo}
           , ctor, ctor.associations.map(_.wireWith)) // ensure pointer equality of contained scala.reflect.Types"""
    }.toList

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
          val withContexts = $withContexts // Referred inside defConstructor, remove FIXME ???

          $defConstructor

          val magnetized = $providerMagnet.apply[$targetType](constructor _)
          val res = new ${weakTypeOf[ProviderMagnet[T]]}($RuntimeDIUniverse.Provider.FactoryProvider.apply(magnetized.get, withContexts))

          new ${weakTypeOf[FactoryConstructor[T]]}(res)
          }
       """
    }
    logger.log(s"Final syntax tree of factory $targetType:\n$res")

    res
  }

}
