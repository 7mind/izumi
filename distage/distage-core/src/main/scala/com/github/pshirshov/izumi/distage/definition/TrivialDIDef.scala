package com.github.pshirshov.izumi.distage.definition

import com.github.pshirshov.izumi.distage.model.DIKey
import com.github.pshirshov.izumi.distage.model.definition.Binding.{SingletonBindingT, _}
import com.github.pshirshov.izumi.distage.model.definition._
import com.github.pshirshov.izumi.distage.model.functions.WrappedFunction
import com.github.pshirshov.izumi.fundamentals.reflection.{EqualitySafeType, _}

import scala.Function.const
import scala.language.experimental.macros


case class TrivialDIDef(bindings: Seq[Binding]) extends ContextDefinition

object TrivialDIDef {

  implicit def TrivialDIDefStart: TrivialDIDef.type => BindingDSL =
    const {
      step(Seq.empty)
    }

  def symbolDef[T: Tag]: ImplDef = ImplDef.TypeImpl(EqualitySafeType.get[T])

  sealed trait BindingDSL {

    protected def bindings: Seq[Binding]

    def finish: TrivialDIDef = TrivialDIDef(bindings)

    def binding[T: Tag]: NameableBinding = {
      namedStep(bindings, SingletonBindingT(DIKey.get[T], symbolDef[T]))
    }

    def binding[T: Tag, I <: T : Tag]: NameableBinding = {
      namedStep(bindings, SingletonBindingT(DIKey.get[T], symbolDef[I]))
    }

    def provider[T: Tag](f: WrappedFunction[T]): NameableBinding = {
      namedStep(bindings, SingletonBindingT(DIKey.get[T], ImplDef.ProviderImpl(f.ret, f)))
    }

    def provider[T: Tag, I <: T : Tag](f: WrappedFunction[I]): NameableBinding = {
      namedStep(bindings, SingletonBindingT(DIKey.get[T], ImplDef.ProviderImpl(f.ret, f)))
    }

    def instance[T: Tag](instance: T): NameableBinding = {
      namedStep(bindings, SingletonBindingT(DIKey.get[T], ImplDef.InstanceImpl(EqualitySafeType.get[T], instance)))
    }

    def magic[T]: NameableBinding = macro MagicMacro.magicMacro[this.type, T, T]

    def magic[T, I <: T]: NameableBinding = macro MagicMacro.magicMacro[this.type, T, I]

    // sets
    def set[T: Tag]: NameableBinding = {
      namedStep(bindings, EmptySetBindingT(DIKey.get[Set[T]]))
    }

    def element[T: Tag, I <: T : Tag]: NameableBinding = {
      namedStep(bindings, SetBindingT(DIKey.get[Set[T]], symbolDef[I]))
    }

    def element[T: Tag](instance: T): NameableBinding = {
      namedStep(bindings, SetBindingT(DIKey.get[Set[T]], ImplDef.InstanceImpl(EqualitySafeType.get[T], instance)))
    }
  }

  class NameableBinding private[TrivialDIDef](private val completed: Seq[Binding], private val current: BindingT[DIKey.TypeKey]) extends BindingDSL {
    override def bindings: Seq[Binding] = completed :+ current.asInstanceOf[Binding]

    def named(name: String): BindingDSL = {
      step(completed :+ current.withTarget[DIKey](current.target.named(name)))
    }
  }

  private def namedStep(completed: Seq[Binding], current: BindingT[DIKey.TypeKey]): NameableBinding = {
    new NameableBinding(completed, current)
  }

  private def step(binds: Seq[Binding]): BindingDSL = new BindingDSL {
    override protected def bindings: Seq[Binding] = binds
  }

}




