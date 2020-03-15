package izumi.distage.constructors.macros

import izumi.distage.constructors.{DebugProperties, FactoryConstructor}
import izumi.distage.model.reflection.Provider
import izumi.distage.model.reflection.universe.StaticDIUniverse
import izumi.distage.reflection.ReflectionProviderDefaultImpl
import izumi.fundamentals.reflection.{ReflectionUtil, TrivialMacroLogger}

import scala.reflect.macros.blackbox

object FactoryConstructorMacro {

  def mkFactoryConstructor[T: c.WeakTypeTag](c: blackbox.Context): c.Expr[FactoryConstructor[T]] = {
    import c.universe._

    val macroUniverse = StaticDIUniverse(c)
    val reflectionProvider = ReflectionProviderDefaultImpl(macroUniverse)
    val logger = TrivialMacroLogger.make[this.type](c, DebugProperties.`izumi.debug.macro.distage.constructors`)

    val targetType = ReflectionUtil.norm(c.universe: c.universe.type)(weakTypeOf[T].dealias)
    requireConcreteTypeConstructor(c)("FactoryConstructor", targetType)

    val impls = FactoryConstructorMacros(c)(macroUniverse)
    import impls.{c => _, u => _, _}

    val macroUniverse.Wiring.Factory.WithProductDeps(factoryMethods, classParameters, methods, factoryProductsDeps) = symbolToFactory(reflectionProvider)(targetType)
    val allParameters = classParameters :+ (methods ++ factoryProductsDeps).map(_.asParameter)

    val provider: c.Expr[Provider.ProviderImpl[T]] = generateProvider[T](allParameters) {
      argss =>
      val dependencyArgMap = allParameters.iterator.flatten.map(_.key).zip(argss.iterator.flatten).toMap
      logger.log(
        s"""Got associations: $allParameters
           |Got argmap: $dependencyArgMap
           |""".stripMargin)

      val traitMethodDefs = methods.zip(argss.last).map {
        case (method, paramSeqIndexTree) => method.traitMethodExpr(paramSeqIndexTree)
      }
      val producerMethodDefs = factoryMethods.map(generateFactoryMethod(dependencyArgMap)(_))

      mkNewAbstractTypeInstanceApplyExpr(targetType, argss.init, traitMethodDefs ++ producerMethodDefs)
    }

    val res = c.Expr[FactoryConstructor[T]](q"{ new ${weakTypeOf[FactoryConstructor[T]]}($provider) }")
    logger.log(s"Final syntax tree of factory $targetType:\n$res")

    res
  }

}
