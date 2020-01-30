package izumi.distage.constructors.macros

import izumi.distage.constructors.{DebugProperties, FactoryConstructor}
import izumi.distage.model.providers.ProviderMagnet
import izumi.distage.model.reflection.macros.ProviderMagnetMacro0
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

    val uttils = ConstructorMacros(c)(macroUniverse)
    import uttils.{c => _, u => _, _}

    val factory = symbolToFactory(reflectionProvider)(targetType)

    val traitMeta = factory.methods.map(mkCtorArgument(_))

    val ((dependencyAssociations, dependencyArgDecls, _), dependencyArgMap: Map[macroUniverse.DIKey.BasicKey, Tree]) = {
      val allMeta = {
        val paramMeta = factory.factoryProductDepsFromObjectGraph.map(mkCtorArgument(_))
        traitMeta ++ paramMeta
      }
      (allMeta.unzip3(CtorArgument.asCtorArgument), allMeta.map { case CtorArgument(param, _, argName) => param.key -> argName }.toMap)
    }

    logger.log(
      s"""Got associations: $dependencyAssociations
         |Got argmap: $dependencyArgMap
         |""".stripMargin)

    val producerMethods = factory.factoryMethods.map(generateFactoryMethod(dependencyArgMap))

    val constructor = {
      val allMethods = producerMethods ++ traitMeta.map(_.traitMethodExpr)
      val instantiate = mkNewAbstractTypeInstanceApplyExpr(targetType, Nil, allMethods)
      q"(..$dependencyArgDecls) => _root_.izumi.distage.constructors.TraitConstructor.wrapInitialization[$targetType]($instantiate)"
    }

    val provider: c.Expr[ProviderMagnet[T]] = {
      val providerMagnetMacro = new ProviderMagnetMacro0[c.type](c)
      providerMagnetMacro.generateProvider[T](
        parameters = dependencyAssociations.asInstanceOf[List[providerMagnetMacro.macroUniverse.Association.Parameter]],
        fun = constructor,
        isGenerated = true
      )
    }
    val res = c.Expr[FactoryConstructor[T]] {
      q"{ new ${weakTypeOf[FactoryConstructor[T]]}($provider) }"
    }
    logger.log(s"Final syntax tree of factory $targetType:\n$res")

    res
  }

}
