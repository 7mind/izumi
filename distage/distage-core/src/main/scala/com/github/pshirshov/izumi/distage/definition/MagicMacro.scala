package com.github.pshirshov.izumi.distage.definition

import com.github.pshirshov.izumi.distage.model.exceptions.UnsupportedWiringException
import com.github.pshirshov.izumi.distage.provisioning.traitcompiler.TraitConstructorMacro
import com.github.pshirshov.izumi.distage.reflection.{SymbolIntrospector, SymbolIntrospectorDefaultImpl}
import com.github.pshirshov.izumi.fundamentals.reflection.{EqualitySafeType, _}


object MagicMacro extends MagicMacro {
  final val symbolIntrospector = SymbolIntrospectorDefaultImpl.instance
  final val traitConstructorMacro = TraitConstructorMacro
}

trait MagicMacro {

  import TrivialDIDef.NameableBinding

  import scala.reflect.macros.whitebox

  def symbolIntrospector: SymbolIntrospector

  def traitConstructorMacro: TraitConstructorMacro

  def magicMacro[THIS: c.WeakTypeTag, T: c.WeakTypeTag, I: c.WeakTypeTag](c: whitebox.Context): c.Expr[NameableBinding] = {
    import c.universe._

    val tType = weakTypeOf[T]
    val iType = weakTypeOf[I]
    val iCastedType = EqualitySafeType(iType.asInstanceOf[TypeNative])

    val self = reify(c.prefix.splice.asInstanceOf[THIS]).tree

    () match {
      case _ if symbolIntrospector.isConcrete(iCastedType) =>
        c.Expr[NameableBinding] {
          if (tType =:= iType) {
            q"{ $self.binding[$tType] }"
          } else {
            q"{ $self.binding[$tType, $iType] }"
          }
        }
      case _ if symbolIntrospector.isFactory(iCastedType) =>
        throw new UnsupportedWiringException(
          s"""
             |Class $iCastedType detected as a factory but
             |Factories are not supported yet! When wiring $tType to $iCastedType"
           """.stripMargin, iCastedType)
      case _ if symbolIntrospector.isWireableAbstract(iCastedType) =>
        val f = traitConstructorMacro.wrappedTestImpl[I](c)

        c.Expr[NameableBinding] {
          if (tType =:= iType) {
            q"{ $self.provider[$tType]({$f}) }"
          } else {
            q"{ $self.provider[$tType, $iType]({$f}) }"
          }
        }
    }

  }

}