package izumi.distage.constructors.macros

import izumi.distage.constructors.{DebugProperties, FactoryConstructor}
import izumi.distage.model.providers.Functoid
import izumi.distage.model.reflection.Provider.ProviderType
import izumi.distage.model.reflection.ReflectionProviderDefaultImpl
import izumi.distage.model.reflection.universe.StaticDIUniverse
import izumi.fundamentals.reflection.{ReflectionUtil, TrivialMacroLogger}

import scala.reflect.macros.blackbox

object FactoryConstructorMacro {

  def mkFactoryConstructor[T: c.WeakTypeTag](c: blackbox.Context): c.Expr[FactoryConstructor[T]] = {
    import c.universe._

    val macroUniverse = StaticDIUniverse(c)
    val reflectionProvider = ReflectionProviderDefaultImpl(macroUniverse)
    val logger = TrivialMacroLogger.make[this.type](c, DebugProperties.`izumi.debug.macro.distage.constructors`.name)

    val targetType = ReflectionUtil.norm(c.universe: c.universe.type)(weakTypeOf[T].dealias)
    requireConcreteTypeConstructor(c)("FactoryConstructor", targetType)

    val impls = FactoryConstructorMacros(c)(macroUniverse)
    import impls.{c => _, u => _, _}

    val macroUniverse.MacroWiring.Factory.WithProductDeps(factoryMethods, classParameters, methods, factoryProductsDeps) = symbolToFactory(reflectionProvider)(targetType)
    val allParameters = classParameters :+ (methods ++ factoryProductsDeps).map(_.asParameter)

    if (factoryMethods.isEmpty) {
      c.abort(c.enclosingPosition, s"No factory methods found in $targetType")
    }

    val provider: c.Expr[Functoid[T]] = generateProvider[T, ProviderType.Factory.type](allParameters) {
      argss =>
        val dependencyArgMap = allParameters.iterator.flatten.map(_.key).zip(argss.iterator.flatten).toMap
        logger.log(s"""Got associations: $allParameters
                      |Got argmap: $dependencyArgMap
                      |""".stripMargin)

        val producerMethodDefs = factoryMethods.map(generateFactoryMethod(dependencyArgMap)(_))

        mkNewAbstractTypeInstanceApplyExpr(targetType, argss.init, producerMethodDefs)
    }

    val res = c.Expr[FactoryConstructor[T]](q"{ new ${weakTypeOf[FactoryConstructor[T]]}($provider) }")
    logger.log(s"Final syntax tree of factory $targetType:\n$res")

    res
  }

}
