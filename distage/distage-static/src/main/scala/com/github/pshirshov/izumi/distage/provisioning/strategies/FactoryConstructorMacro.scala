package com.github.pshirshov.izumi.distage.provisioning.strategies

import com.github.pshirshov.izumi.distage.model.functions.WrappedFunction.DIKeyWrappedFunction
import com.github.pshirshov.izumi.distage.model.provisioning.FactoryExecutor
import com.github.pshirshov.izumi.distage.model.reflection.universe.{RuntimeDIUniverse, StaticDIUniverse}
import com.github.pshirshov.izumi.distage.provisioning.{AbstractConstructor, FactoryConstructor, FactoryTools}
import com.github.pshirshov.izumi.distage.reflection.{DependencyKeyProviderDefaultImpl, ReflectionProviderDefaultImpl, SymbolIntrospectorDefaultImpl}
import com.github.pshirshov.izumi.fundamentals.reflection.MacroUtil

import scala.reflect.macros.blackbox

// TODO: Factories can exceed 22 arguments limit on function parameter list
object FactoryConstructorMacro {

  def mkFactoryConstructor[T: c.WeakTypeTag](c: blackbox.Context): c.Expr[FactoryConstructor[T]] = {
    import c.universe._

    val macroUniverse = StaticDIUniverse(c)
    import macroUniverse.Association._
    import macroUniverse.Wiring._
    import macroUniverse._

    val keyProvider = DependencyKeyProviderDefaultImpl.Static.instance(macroUniverse)
    val symbolIntrospector = SymbolIntrospectorDefaultImpl.Static.instance(macroUniverse)
    val reflectionProvider = ReflectionProviderDefaultImpl.Static.instance(macroUniverse)(keyProvider, symbolIntrospector)
    val logger = MacroUtil.mkLogger[this.type](c)

    val targetType = weakTypeOf[T]

    val factoryInfo@FactoryMethod(_, wireables, dependencies) = reflectionProvider.symbolToWiring(
      SafeType(targetType)
    )

    val (dependencyArgs, dependencyMethods) = dependencies.map {
      // FIXME: FIXME COPYPASTA with below and with TraitStrategyMacro
      case Method(_, methodSymbol, targetKey) =>
        val tpe = targetKey.symbol.tpe
        val methodName = methodSymbol.asMethod.name.toTermName
        val argName = c.freshName(methodName)

        val anns = targetKey match {
          case idKey: DIKey.IdKey[_] =>
            import idKey._
            val ann = q"new _root_.com.github.pshirshov.izumi.distage.model.definition.Id($id)"
            Modifiers.apply(NoFlags, typeNames.EMPTY, List(ann))
          case _ =>
            Modifiers()
        }

        (q"$anns val $argName: $tpe", q"override val $methodName: $tpe = $argName")
    }.unzip

    // FIXME transitive dependencies request (HACK pulling up dependencies from factory methods to ensure correct plan ordering)
    val transitiveDependenciesArgsHACK = factoryInfo.associations.map {
          // FIXME: FIXME COPYPASTA with above and with TraitStrategyMacro
      assoc =>
        val key = assoc.wireWith

        val anns = key match {
          case idKey: DIKey.IdKey[_] =>
            import idKey._
            val ann = q"new _root_.com.github.pshirshov.izumi.distage.model.definition.Id($id)"
            Modifiers.apply(NoFlags, typeNames.EMPTY, List(ann))
          case _ =>
            Modifiers()
        }

        val typeFull = key.symbol
        q"$anns val ${TermName(c.freshName("transitive"))}: ${typeFull.tpe}"
    }

    val (executorName, executorType) = TermName(c.freshName("executor")) -> typeOf[FactoryExecutor].typeSymbol
    val executorArg = q"$executorName: $executorType"
    val factoryTools = symbolOf[FactoryTools.type].asClass.module

    // FIXME we can't remove runtime dependency on scala.reflect right now because:
    //  1. provisioner depends on RuntimeUniverse scala.reflect Types
    //  2. we need to lift DIKey & SafeType types (by calling RuntimeUniverse reflection)
    //
    //  Solution:
    //    * Provisioning shouldn't call reflection, all necessary info should be collected by planner
    //    * Universe types(DIKey, SafeType) should be interfaces not directly referencing scala-reflect types
    //    * API builder methods shouldn't require TypeTag
    //    * make scala-reflect dependency (% Provided) for distage-static
    //      * provided dependencies seem to be correctly transitive, at least shapeless doesn't require the user to
    //        to add provided scala-reflect to his .sbt
    val producerMethods = wireables.map {
      case FactoryMethod.WithContext(factoryMethod, productConstructor, methodArguments) =>

        val (methodArgs, executorArgs) = methodArguments.map {
          dIKey =>
            val name = TermName(c.freshName())
            q"$name: ${dIKey.symbol.tpe}" -> q"{ $name }"
        }.unzip

        val typeParams = factoryMethod.typeParams.map(ty => c.internal.typeDef(ty))

        val resultTypeOfMethod = factoryMethod.typeSignature.finalResultType
        val instanceType = productConstructor.instanceType

        val wiringInfo = productConstructor match {
          case w: UnaryWiring.Constructor =>
            q"{ $w }"
          case w: UnaryWiring.Abstract =>
            q"""{
            val fun = ${symbolOf[AbstractConstructor.type].asClass.module}.apply[${w.instanceType.tpe}].function
            $RuntimeDIUniverse.Wiring.UnaryWiring.Function.apply(fun)
            }"""
        }

        q"""
        override def ${factoryMethod.name}[..$typeParams](..$methodArgs): $resultTypeOfMethod = {
          val symbolDeps: ${typeOf[RuntimeDIUniverse.Wiring.UnaryWiring]} = $wiringInfo

          val executorArgs: ${typeOf[Map[RuntimeDIUniverse.DIKey, Any]]} =
            ${if (executorArgs.nonEmpty)
              // ensure referential equality for typetags inside symbolDeps and inside executableOps (to support weakTypeTag generics)
              q"{ symbolDeps.associations.map(_.wireWith).zip(${executorArgs.toList}).toMap }"
             else
              q"{ ${Map.empty[Unit, Unit]} } "
             }

          $factoryTools.interpret(
            $executorName.execute(
              executorArgs
              , $factoryTools.mkExecutableOp(
                  ${DIKey.TypeKey(instanceType)}
                  , symbolDeps
                )
            )
          ).asInstanceOf[$resultTypeOfMethod]
        }
        """
    }

    val allArgs = (executorArg +: dependencyArgs) ++ transitiveDependenciesArgsHACK
    val allMethods = producerMethods ++ dependencyMethods
    val instantiate = if (allMethods.isEmpty)
      q"new $targetType {}"
    else
      q"new $targetType { ..$allMethods }"

    val defConstructor =
      q"""
      def constructor(..$allArgs): $targetType =
        ($instantiate).asInstanceOf[$targetType]
      """

    val dikeyWrappedFunction = symbolOf[DIKeyWrappedFunction.type].asClass.module
    val res = c.Expr[FactoryConstructor[T]] {
      q"""
          {
          $defConstructor

          new ${weakTypeOf[FactoryConstructor[T]]}($dikeyWrappedFunction.apply[$targetType](constructor _))
          }
       """
    }
    logger.log(s"Final syntax tree of factory $targetType:\n$res")

    res
  }

}
