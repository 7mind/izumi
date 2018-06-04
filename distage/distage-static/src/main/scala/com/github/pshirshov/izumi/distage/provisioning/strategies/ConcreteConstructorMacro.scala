package com.github.pshirshov.izumi.distage.provisioning.strategies

import com.github.pshirshov.izumi.distage.model.providers.ProviderMagnet
import com.github.pshirshov.izumi.distage.model.reflection.universe.StaticDIUniverse
import com.github.pshirshov.izumi.distage.provisioning.ConcreteConstructor
import com.github.pshirshov.izumi.distage.reflection.{DependencyKeyProviderDefaultImpl, ReflectionProviderDefaultImpl, SymbolIntrospectorDefaultImpl}
import com.github.pshirshov.izumi.fundamentals.reflection.MacroUtil

import scala.reflect.macros.blackbox

object ConcreteConstructorMacro {

  def mkConcreteConstructor[T: c.WeakTypeTag](c: blackbox.Context): c.Expr[ConcreteConstructor[T]] = {
    import c.universe._

    val macroUniverse = StaticDIUniverse(c)
    import macroUniverse.Wiring._
    import macroUniverse._

    val symbolIntrospector = SymbolIntrospectorDefaultImpl.Static(macroUniverse)
    val keyProvider = DependencyKeyProviderDefaultImpl.Static(macroUniverse)(symbolIntrospector)
    val reflectionProvider = ReflectionProviderDefaultImpl.Static(macroUniverse)(keyProvider, symbolIntrospector)
    val logger = MacroUtil.mkLogger[this.type](c)

    val targetType = weakTypeOf[T]

    val UnaryWiring.Constructor(_, associations) = reflectionProvider.symbolToWiring(SafeType(targetType))

    val (args, argNames) = associations.map {
      p =>
        val name = c.freshName(TermName(p.name))
        q"val $name: ${p.wireWith.tpe.tpe}" -> name
    }.toList.unzip

    val providerMagnet = symbolOf[ProviderMagnet.type].asClass.module

    val res = c.Expr[ConcreteConstructor[T]] {
      q"""{
          new ${weakTypeOf[ConcreteConstructor[T]]}(
            $providerMagnet.apply[$targetType](
              (..$args) => new $targetType(..$argNames)
            )
          )
       }"""
    }
    logger.log(s"Final syntax tree of concrete constructor for $targetType:\n$res")

    res
  }
}

