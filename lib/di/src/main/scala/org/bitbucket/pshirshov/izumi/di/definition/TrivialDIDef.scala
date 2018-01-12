package org.bitbucket.pshirshov.izumi.di.definition

import org.bitbucket.pshirshov.izumi.di.definition.Binding.{EmptySetBinding, SetBinding, SingletonBinding}
import org.bitbucket.pshirshov.izumi.di.model.DIKey
import org.bitbucket.pshirshov.izumi.di.{TypeFull, Tag}

case class TrivialDIDef(bindings: Seq[Binding]) extends ContextDefinition

object TrivialDIDef {

  def symbolDef[T: Tag]: ImplDef = ImplDef.TypeImpl(typeSymbol)

  def typeSymbol[T: Tag]: TypeFull = {
    import scala.reflect.runtime.universe._
    typeTag[T].tpe
  }

  class BindingSupport(bindings: Seq[Binding]) {
    def nameless[T: Tag]: BindingSupport = {
      new BindingSupport(bindings :+ SingletonBinding(DIKey.get[T], symbolDef[T]))
    }

    def nameless[T: Tag, I <: T : Tag]: BindingSupport = {
      new BindingSupport(bindings :+ SingletonBinding(DIKey.get[T], symbolDef[I]))
    }

    def nameless[T: Tag](instance: T): BindingSupport = {
      new BindingSupport(bindings :+ SingletonBinding(DIKey.get[T], ImplDef.InstanceImpl(typeSymbol[T], instance)))
    }


    def named[T: Tag](name: String): BindingSupport = {
      new BindingSupport(bindings :+ SingletonBinding(DIKey.get[T].named(name), symbolDef[T]))
    }

    def named[T: Tag, I <: T : Tag](name: String): BindingSupport = {
      new BindingSupport(bindings :+ SingletonBinding(DIKey.get[T].named(name), symbolDef[I]))
    }

    def named[T: Tag](instance: T, name: String): BindingSupport = {
      new BindingSupport(bindings :+ SingletonBinding(DIKey.get[T].named(name), ImplDef.InstanceImpl(typeSymbol[T], instance)))
    }


    def namelessEmptySet[T: Tag]: BindingSupport = {
      new BindingSupport(bindings :+ EmptySetBinding(DIKey.get[Set[T]]))

    }

    def namedEmptySet[T: Tag](name: String): BindingSupport = {
      new BindingSupport(bindings :+ EmptySetBinding(DIKey.get[Set[T]].named(name)))

    }

    def namelessSet[T: Tag, I <: T : Tag]: BindingSupport = {
      new BindingSupport(bindings :+ SetBinding(DIKey.get[Set[T]], symbolDef[I]))
    }

    def namelessSet[T: Tag](instance: T): BindingSupport = {
      new BindingSupport(bindings :+ SetBinding(DIKey.get[Set[T]], ImplDef.InstanceImpl(typeSymbol[T], instance)))
    }

    def namedSet[T: Tag, I <: T : Tag](name: String): BindingSupport = {
      new BindingSupport(bindings :+ SetBinding(DIKey.get[Set[T]].named(name), symbolDef[I]))
    }

    def namedSet[T: Tag](instance: T, name: String): BindingSupport = {
      new BindingSupport(bindings :+ SetBinding(DIKey.get[Set[T]].named(name), ImplDef.InstanceImpl(typeSymbol[T], instance)))
    }


    def finish: ContextDefinition = TrivialDIDef(bindings)
  }

  final val empty: BindingSupport = new BindingSupport(Seq.empty)
}