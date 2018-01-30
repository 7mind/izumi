package com.github.pshirshov.izumi.distage.definition

import com.github.pshirshov.izumi.distage.model.definition.BindingDSL.NameableBinding
import com.github.pshirshov.izumi.distage.model.exceptions.UnsupportedWiringException
import com.github.pshirshov.izumi.distage.model.reflection.universe.MacroUniverse
import com.github.pshirshov.izumi.distage.provisioning.strategies.{FactoryStrategyMacro, FactoryStrategyMacroDefaultImpl, TraitStrategyMacro, TraitStrategyMacroDefaultImpl}
import com.github.pshirshov.izumi.distage.reflection.SymbolIntrospectorDefaultImpl

import scala.reflect.macros.blackbox


object MagicMacro extends MagicMacro {
  override final val traitStrategyMacro = TraitStrategyMacroDefaultImpl
  override final val factoryStrategyMacro: FactoryStrategyMacro = FactoryStrategyMacroDefaultImpl
}

trait MagicMacro {

  def traitStrategyMacro: TraitStrategyMacro
  def factoryStrategyMacro: FactoryStrategyMacro

  def magicMacro[THIS: c.WeakTypeTag, T: c.WeakTypeTag, I: c.WeakTypeTag](c: blackbox.Context): c.Expr[NameableBinding] = {
    import c.universe._

    val macroUniverse = MacroUniverse(c)
    val symbolIntrospector = SymbolIntrospectorDefaultImpl.Macro.instance(macroUniverse)
    import macroUniverse._

    val tType = weakTypeOf[T]
    val safeT = SafeType(tType)
    val iType = weakTypeOf[I]
    val safeI = SafeType(iType)

    val self = q"${reify(c.prefix.splice.asInstanceOf[THIS]).tree}.self"

    if (symbolIntrospector.isConcrete(safeI)) {
      c.Expr[NameableBinding] {
        if (safeT == safeI) {
          q"{ $self.binding[$tType] }"
        } else {
          q"{ $self.binding[$tType, $iType] }"
        }
      }
    } else if (symbolIntrospector.isFactory(safeI)) {
      val f = factoryStrategyMacro.mkWrappedFactoryConstructorMacro[I](c)

      c.Expr[NameableBinding] {
        if (safeT == safeI) {
          q"{ $self.provider[$tType]({$f}) }"
        } else {
          q"{ $self.provider[$tType, $iType]({$f}) }"
        }
      }
    } else if (symbolIntrospector.isWireableAbstract(safeI)) {
      val f = traitStrategyMacro.mkWrappedTraitConstructorMacro[I](c)

      c.Expr[NameableBinding] {
        if (safeT == safeI) {
          q"{ $self.provider[$tType]({$f}) }"
        } else {
          q"{ $self.provider[$tType, $iType]({$f}) }"
        }
      }
    } else {
      throw new UnsupportedWiringException(
        s"""
           |The impossible happened! Tried to wire class $iType, but it's neither concrete, nor a trait, nor factory,
           | nor we can provide a better error message because we can't classify it! When wiring $tType to $iType
         """.stripMargin
      , safeI)
    }

  }

}
