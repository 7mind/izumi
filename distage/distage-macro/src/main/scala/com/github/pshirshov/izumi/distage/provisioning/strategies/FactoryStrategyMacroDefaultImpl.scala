package com.github.pshirshov.izumi.distage.provisioning.strategies

import com.github.pshirshov.izumi.distage.model.functions.WrappedFunction
import com.github.pshirshov.izumi.distage.model.provisioning.FactoryExecutor
import com.github.pshirshov.izumi.distage.model.reflection.universe.{MacroUniverse, RuntimeUniverse}
import com.github.pshirshov.izumi.distage.provisioning.FactoryTools
import com.github.pshirshov.izumi.distage.reflection.{DependencyKeyProviderDefaultImpl, ReflectionProviderDefaultImpl, SymbolIntrospectorDefaultImpl}

import scala.language.experimental.macros
import scala.reflect.macros.blackbox

trait FactoryStrategyMacroDefaultImpl {
  self: FactoryStrategyMacro =>

  def mkWrappedFactoryConstructor[T]: WrappedFunction[T] = macro FactoryStrategyMacroDefaultImplImpl.mkWrappedFactoryConstructorMacro[T]

  @inline
  // reason for this is simply IDEA flipping out on [T: c.WeakTypeTag]
  override def mkWrappedFactoryConstructorMacro[T: blackbox.Context#WeakTypeTag](c: blackbox.Context): c.Expr[WrappedFunction[T]] =
    FactoryStrategyMacroDefaultImplImpl.mkWrappedFactoryConstructorMacro[T](c)
}

object FactoryStrategyMacroDefaultImpl
  extends FactoryStrategyMacroDefaultImpl
    with FactoryStrategyMacro

// TODO: Factories can exceed 22 arguments limit on function parameter list
// TODO: Preserve annotations to support IDs

// reason for this indirection is just to avoid slowdown from red lines in idea on ```object FactoryStrategyMacroDefaultImpl extends FactoryStrategyMacro with FactoryStrategyMacroDefaultImpl```
private[strategies] object FactoryStrategyMacroDefaultImplImpl {


  def mkWrappedFactoryConstructorMacro[T: c.WeakTypeTag](c: blackbox.Context): c.Expr[WrappedFunction[T]] = {
    import c.universe._

    val macroUniverse = MacroUniverse(c)
    import macroUniverse.Wiring._
    import macroUniverse._

    val keyProvider = DependencyKeyProviderDefaultImpl.Macro.instance(macroUniverse)
    val symbolIntrospector = SymbolIntrospectorDefaultImpl.Macro.instance(macroUniverse)
    val reflectionProvider = ReflectionProviderDefaultImpl.Macro.instance(macroUniverse)(
      keyProvider
      , symbolIntrospector
    )

    val targetType = weakTypeOf[T]

    val factoryInfo@FactoryMethod(_, wireables, dependencies) = reflectionProvider.symbolToWiring(
      SafeType(targetType)
    )

    val (dependencyArgs, dependencyMethods) = dependencies.map {
      dep =>
        val methodName = dep.symbol.name.toTermName
        val argName = c.freshName(methodName)
        val tpe = dep.wireWith.symbol.tpe

        (q"$argName: $tpe",  q"override val $methodName: $tpe = $argName")
    }.unzip

    val producerArgs = factoryInfo.associations.map {
      assoc =>
        val typeFull = assoc.wireWith.symbol
        q"${TermName(c.freshName(typeFull.toString))}: ${typeFull.tpe}"
    }

    val (executorName, executorType) = TermName(c.freshName("executor")) -> typeOf[FactoryExecutor].typeSymbol
    val executorArg = q"$executorName: $executorType"
    // FIXME if unarySymbolDeps are unneded remove singletons
    // ???
    val factoryTools = symbolOf[FactoryTools.type].asClass.module

    // FIXME we can't remove runtime dependency on scala.reflect right now because:
    //  1. provisioner depends on RuntimeUniverse scala.reflect Types
    //  2. we need to lift DIKey & SafeType types (by calling RuntimeUniverse reflection)
    val producerMethods = wireables.map {
      case FactoryMethod.WithContext(factoryMethod, wireWith, methodArguments) =>

        val (methodArgs, executorArgs)  = methodArguments.map {
          dIKey =>
            val name = TermName(c.freshName(dIKey.symbol.toString))
            q"$name: ${dIKey.symbol.tpe}" -> q"{ $name }"
        }.unzip

        val typeParams = factoryMethod.typeParams.map(ty => c.internal.typeDef(ty))

        val resultTypeOfMethod = factoryMethod.typeSignature.finalResultType
        val instanceType = wireWith.instanceType

        val wiringInfo = wireWith match {
          case w: UnaryWiring.Constructor =>
            q"{ $w }"
          case w: UnaryWiring.Abstract => q"""{
            val fun = ${symbolOf[TraitStrategyMacroDefaultImpl.type].asClass.module}.mkWrappedTraitConstructor[${w.instanceType.tpe}]
            ${symbolOf[RuntimeUniverse.type].asClass.module}.Wiring.UnaryWiring.Function.apply(fun)
            }"""
        }

        q"""
        override def ${factoryMethod.name}[..$typeParams](..$methodArgs): $resultTypeOfMethod = {
          val symbolDeps: ${typeOf[RuntimeUniverse.Wiring.UnaryWiring]} = $wiringInfo

          val executorArgs: ${typeOf[Map[RuntimeUniverse.DIKey, Any]]} =
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

    val allArgs = (executorArg +: dependencyArgs) ++ producerArgs
    val allMethods = producerMethods ++ dependencyMethods
    val instantiate = if (allMethods.isEmpty)
      q"new $targetType {}"
    else
      q"new $targetType { ..$allMethods }"

    val expr = c.Expr {
      q"""
      {
      def factoryConstructor(..$allArgs): $targetType =
        ($instantiate).asInstanceOf[$targetType]

      (factoryConstructor _)
      }
      """
    }

    val WrappedFunction = typeOf[WrappedFunction[_]].typeSymbol
    val res = c.Expr[WrappedFunction[T]] {
      q"""
          {
          val ctor = ${reify(expr.splice)}

          // trigger implicit conversion
          _root_.scala.Predef.identity[$WrappedFunction[$targetType]](ctor)
          }
       """
    }
    c.info(c.enclosingPosition, s"Syntax tree of factory $targetType:\n$res", force = false)

    res
  }

}
