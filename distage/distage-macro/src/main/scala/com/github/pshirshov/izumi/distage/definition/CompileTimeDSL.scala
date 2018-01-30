package com.github.pshirshov.izumi.distage.definition

import com.github.pshirshov.izumi.distage.model.definition.BindingDSL
import com.github.pshirshov.izumi.distage.model.definition.BindingDSL.NameableBinding

import scala.language.experimental.macros

object CompileTimeDSL {

  implicit final class CompileTimeBindingDSL(val self: BindingDSL) extends AnyVal {
    def magic[T]: NameableBinding = macro MagicMacro.magicMacro[CompileTimeBindingDSL, T, T]

    def magic[T, I <: T]: NameableBinding = macro MagicMacro.magicMacro[CompileTimeBindingDSL, T, I]
  }

}

  // can use implicit materializer to remove references to .self in macro and make it private
  // but requires DummyImplicit in API:
  //
  // object CompileTimeDSL {
  //  implicit def magicMacroImpl[T, I <: T]: MagicMacroRes[T, I] = macro MagicMacro.magicMacro[Nothing, T, I]
  //
  //  implicit final class CompileTimeBindingDSL(private val dsl: BindingDSL) extends AnyVal {
  //    def magic[T](implicit magicMacroRes: MagicMacroRes[T, T]): NameableBinding = magicMacroRes.apply(dsl)
  //
  //    def magic[T, I <: T](implicit magicMacroRes: MagicMacroRes[T, I], dummyImplicit: DummyImplicit): NameableBinding = magicMacroRes.apply(dsl)
  //  }
  // }
