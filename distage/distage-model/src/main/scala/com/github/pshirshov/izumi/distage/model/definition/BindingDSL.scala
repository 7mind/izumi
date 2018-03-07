package com.github.pshirshov.izumi.distage.model.definition

import com.github.pshirshov.izumi.distage.model.definition.BindingT.{EmptySetBindingT, SetBindingT, SingletonBindingT}
import com.github.pshirshov.izumi.distage.model.functions.WrappedFunction
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeUniverse

trait BindingDSL extends ContextDefinition {
  import BindingDSL._

  def bindings: Seq[Binding]

  def binding[T: RuntimeUniverse.Tag]: NameableBinding = {
    namedStep(bindings, SingletonBindingT(RuntimeUniverse.DIKey.get[T], ImplDef.TypeImpl(RuntimeUniverse.SafeType.get[T])))
  }

  def binding[T: RuntimeUniverse.Tag, I <: T: RuntimeUniverse.Tag]: NameableBinding = {
    namedStep(bindings, SingletonBindingT(RuntimeUniverse.DIKey.get[T], ImplDef.TypeImpl(RuntimeUniverse.SafeType.get[I])))
  }

  def provider[T: RuntimeUniverse.Tag](f: WrappedFunction[T]): NameableBinding = {
    namedStep(bindings, SingletonBindingT(RuntimeUniverse.DIKey.get[T], ImplDef.ProviderImpl(f.ret, f)))
  }

  def provider[T: RuntimeUniverse.Tag, I <: T: RuntimeUniverse.Tag](f: WrappedFunction[I]): NameableBinding = {
    namedStep(bindings, SingletonBindingT(RuntimeUniverse.DIKey.get[T], ImplDef.ProviderImpl(f.ret, f)))
  }

  def instance[T: RuntimeUniverse.Tag](instance: T): NameableBinding = {
    namedStep(bindings, SingletonBindingT(RuntimeUniverse.DIKey.get[T], ImplDef.InstanceImpl(RuntimeUniverse.SafeType.get[T], instance)))
  }

  // sets
  def set[T: RuntimeUniverse.Tag]: NameableBinding = {
    namedStep(bindings, EmptySetBindingT(RuntimeUniverse.DIKey.get[Set[T]]))
  }

  def element[T: RuntimeUniverse.Tag, I <: T : RuntimeUniverse.Tag]: NameableBinding = {
    namedStep(bindings, SetBindingT(RuntimeUniverse.DIKey.get[Set[T]], ImplDef.TypeImpl(RuntimeUniverse.SafeType.get[I])))
  }

  def element[T: RuntimeUniverse.Tag](instance: T): NameableBinding = {
    namedStep(bindings, SetBindingT(RuntimeUniverse.DIKey.get[Set[T]], ImplDef.InstanceImpl(RuntimeUniverse.SafeType.get[T], instance)))
  }

  private def namedStep(completed: Seq[Binding], current: BindingT[RuntimeUniverse.DIKey.TypeKey]): NameableBinding = {
    new NameableBinding(completed, current)
  }
}

object BindingDSL {

  class NameableBinding private[BindingDSL] (private val completed: Seq[Binding], private val current: BindingT[RuntimeUniverse.DIKey.TypeKey]) extends BindingDSL {
    override def bindings: Seq[Binding] = completed :+ current

    def named(name: String): BindingDSL = {
      step(completed :+ current.withTarget[RuntimeUniverse.DIKey](current.target.named(name)))
    }
  }

  private def step(binds: Seq[Binding]): BindingDSL = new BindingDSL {
    override def bindings: Seq[Binding] = binds
  }

}
