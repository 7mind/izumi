package org.bitbucket.pshirshov.izumi.di.definition

import org.bitbucket.pshirshov.izumi.di.Tag
import org.bitbucket.pshirshov.izumi.di.definition.Binding.{EmptySetBinding, SetBinding, SingletonBinding}
import org.bitbucket.pshirshov.izumi.di.model.{DIKey, EqualitySafeType}

case class TrivialDIDef(bindings: Seq[Binding]) extends ContextDefinition


object TrivialDIDef {

  def symbolDef[T:Tag]: ImplDef = ImplDef.TypeImpl(EqualitySafeType.get[T])

  class NamedSupport(name: String, bindings: Seq[Binding]) {
    def binding[T: Tag]: BindingSupport = {
      new BindingSupport(bindings :+ SingletonBinding(DIKey.get[T].named(name), symbolDef[T]))
    }

    def binding[T: Tag, I <: T : Tag]: BindingSupport = {
      new BindingSupport(bindings :+ SingletonBinding(DIKey.get[T].named(name), symbolDef[I]))
    }

    def instance[T: Tag](instance: T): BindingSupport = {
      new BindingSupport(bindings :+ SingletonBinding(DIKey.get[T].named(name), ImplDef.InstanceImpl(EqualitySafeType.get[T], instance)))
    }

    def provider[T: Tag](f: WrappedFunction[T]): BindingSupport = {
      new BindingSupport(bindings :+ SingletonBinding(DIKey.get[T].named(name), ImplDef.ProviderImpl(f.ret, f)))
    }

    def set[T: Tag]: BindingSupport = {
      new BindingSupport(bindings :+ EmptySetBinding(DIKey.get[Set[T]].named(name)))
    }

    def element[T: Tag, I <: T : Tag]: BindingSupport = {
      new BindingSupport(bindings :+ SetBinding(DIKey.get[Set[T]].named(name), symbolDef[I]))
    }

    def element[T: Tag](instance: T): BindingSupport = {
      new BindingSupport(bindings :+ SetBinding(DIKey.get[Set[T]].named(name), ImplDef.InstanceImpl(EqualitySafeType.get[T], instance)))
    }

  }

  class BindingSupport(bindings: Seq[Binding]) {
    def named(name: String) = new NamedSupport(name, bindings)

    def binding[T: Tag]: BindingSupport = {
      new BindingSupport(bindings :+ SingletonBinding(DIKey.get[T], symbolDef[T]))
    }

    def provider[T: Tag](f: WrappedFunction[T]): BindingSupport = {
      new BindingSupport(bindings :+ SingletonBinding(DIKey.get[T], ImplDef.ProviderImpl(f.ret, f)))
    }

    def binding[T: Tag, I <: T : Tag]: BindingSupport = {
      new BindingSupport(bindings :+ SingletonBinding(DIKey.get[T], symbolDef[I]))
    }

    def instance[T: Tag](instance: T): BindingSupport = {
      new BindingSupport(bindings :+ SingletonBinding(DIKey.get[T], ImplDef.InstanceImpl(EqualitySafeType.get[T], instance)))
    }

    // sets
    def set[T: Tag]: BindingSupport = {
      new BindingSupport(bindings :+ EmptySetBinding(DIKey.get[Set[T]]))
    }


    def element[T: Tag, I <: T : Tag]: BindingSupport = {
      new BindingSupport(bindings :+ SetBinding(DIKey.get[Set[T]], symbolDef[I]))
    }

    def element[T: Tag](instance: T): BindingSupport = {
      new BindingSupport(bindings :+ SetBinding(DIKey.get[Set[T]], ImplDef.InstanceImpl(EqualitySafeType.get[T], instance)))
    }

    def finish: ContextDefinition = TrivialDIDef(bindings)
  }

  final val empty: BindingSupport = new BindingSupport(Seq.empty)
}
