package com.github.pshirshov.izumi.distage.provisioning.strategies

import com.github.pshirshov.izumi.distage.model.definition.reflection.DIUniverseMacros
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

    val symbolIntrospector = SymbolIntrospectorDefaultImpl.Static(macroUniverse)
    val keyProvider = DependencyKeyProviderDefaultImpl.Static(macroUniverse)(symbolIntrospector)
    val reflectionProvider = ReflectionProviderDefaultImpl.Static(macroUniverse)(keyProvider, symbolIntrospector)
    val tools = DIUniverseMacros(macroUniverse)
    val logger = MacroUtil.mkLogger[this.type](c)

    import macroUniverse.Association._
    import macroUniverse.Wiring._
    import macroUniverse._
    import tools._

    val targetType = weakTypeOf[T]

    val factoryInfo@FactoryMethod(_, wireables, dependencies) = reflectionProvider.symbolToWiring(
      SafeType(targetType)
    )

    val (dependencyArgs, dependencyMethods) = dependencies.map {
      case AbstractMethod(_, methodSymbol, key) =>
        val tpe = key.tpe.tpe
        val methodName: TermName = TermName(methodSymbol.name)
        val argName: TermName = c.freshName(methodName)

        val mods = tools.modifiersForAnns(methodSymbol.annotations)

        (q"$mods val $argName: $tpe", q"override val $methodName: $tpe = $argName")
    }.unzip

    // FIXME transitive dependencies request (HACK pulling up dependencies from factory methods to ensure correct plan ordering)
    val transitiveDependenciesArgsHACK = factoryInfo.associations.map {
      assoc =>
        val key = assoc.wireWith
        val anns = tools.modifiersForAnns(assoc.symbol.annotations)

        q"$anns val ${TermName(c.freshName("transitive"))}: ${key.tpe.tpe}"
    }

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
    val producerMethods = wireables.map {
      case FactoryMethod.WithContext(factoryMethod, productConstructor, methodArguments) =>

        val (methodArgs, executorArgs) = methodArguments.map {
          dIKey =>
            val name = TermName(c.freshName())
            q"$name: ${dIKey.tpe.tpe}" -> q"{ $name }"
        }.unzip

        val typeParams = factoryMethod.typeParams.map(ty => c.internal.typeDef(ty))

        val resultTypeOfMethod = factoryMethod.typeSignature.finalResultType
        val instanceType = productConstructor.instanceType

        val wiringInfo = productConstructor match {
          case w: UnaryWiring.Constructor =>
            val associations: List[Tree] = w.associations.map {
              case Association.Parameter(context: DependencyContext.ConstructorParameterContext, symb, wireWith) =>
                q"{ new $RuntimeDIUniverse.Association.Parameter($context, $symb, $wireWith)}"
              case Association.Parameter(context, _, _) =>
                c.abort(c.enclosingPosition, s"Expected ConstructorParameterContext but got $context")
            }.toList

            q"{ $RuntimeDIUniverse.Wiring.UnaryWiring.Constructor(${w.instanceType}, $associations) }"

          case w: UnaryWiring.AbstractSymbol =>
            q"""{
            val fun = ${symbolOf[AbstractConstructor.type].asClass.module}.apply[${w.instanceType.tpe}].function

            val context = $RuntimeDIUniverse.DependencyContext.CallableParameterContext(fun)

            val associations = fun.diKeys.map($RuntimeDIUniverse.Association.Parameter.fromDIKey(context, _))

            $RuntimeDIUniverse.Wiring.UnaryWiring.Function.apply(fun, associations)
            }"""
        }

        q"""
        override def ${factoryMethod.name}[..$typeParams](..$methodArgs): $resultTypeOfMethod = {
          val symbolDeps: ${typeOf[RuntimeDIUniverse.Wiring.UnaryWiring]} = $wiringInfo

          val executorArgs: ${typeOf[Map[RuntimeDIUniverse.DIKey, Any]]} =
            ${
          if (executorArgs.nonEmpty)
          // ensure pointer equality for typetags inside symbolDeps and inside executableOps
          // to support weakTypeTag-based generics since they are pointer equality based
          {
            q"{ symbolDeps.associations.map(_.wireWith).zip(${executorArgs.toList}).toMap }"
          } else {
            q"{ ${Map.empty[Unit, Unit]} } "
          }
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

    val executorArg = q"$executorName: $executorType"
    val allArgs = (executorArg +: dependencyArgs) ++ transitiveDependenciesArgsHACK
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
