package com.github.pshirshov.izumi.distage.model.definition

import com.github.pshirshov.izumi.distage.model.definition.BindingDSL.{BindDSL, BindOnlyNameableDSL}
import com.github.pshirshov.izumi.distage.model.reflection.universe.StaticDIUniverse
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

  def magicMacro[THIS: c.WeakTypeTag, T: c.WeakTypeTag, I: c.WeakTypeTag](c: blackbox.Context): c.Expr[BindOnlyNameableDSL] = {
    import c.universe._

    val macroUniverse = StaticDIUniverse(c)
    val symbolIntrospector = SymbolIntrospectorDefaultImpl.Static.instance(macroUniverse)
    import macroUniverse._

    val tType = weakTypeOf[T]
    val safeT = SafeType(tType)
    val iType = weakTypeOf[I]
    val safeI = SafeType(iType)

    val self = q"${reify(c.prefix.splice.asInstanceOf[THIS]).tree}.self"

    if (symbolIntrospector.isConcrete(safeI)) {
      c.Expr[BindDSL[T]] {
        if (safeT == safeI) {
          q"{ $self.bind[$tType] }"
        } else {
          q"{ $self.bind[$tType].as[$iType] }"
        }
      }
    } else if (symbolIntrospector.isFactory(safeI)) {
      val f = factoryStrategyMacro.mkWrappedFactoryConstructorMacro[I](c)

      c.Expr[BindOnlyNameableDSL] {
        q"{ $self.bind[$tType].provided[$tType]({$f}) }"
      }
    } else if (symbolIntrospector.isWireableAbstract(safeI)) {
      val f = traitStrategyMacro.mkWrappedTraitConstructorMacro[I](c)

      c.Expr[BindOnlyNameableDSL] {
        q"{ $self.bind[$tType].provided[$tType]({$f}) }"
      }
    } else {
      c.abort(c.enclosingPosition
        , s"""
           |The impossible happened! Tried to wire class $iType, but it's neither concrete, nor a trait, nor factory,
           | nor we can provide a better error message because we can't classify it! When wiring $tType to $iType
         """.stripMargin
      )
    }

  }

}
