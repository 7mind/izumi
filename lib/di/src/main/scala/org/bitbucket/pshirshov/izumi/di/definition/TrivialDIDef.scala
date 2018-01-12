package org.bitbucket.pshirshov.izumi.di.definition

import org.bitbucket.pshirshov.izumi.di.definition.Def.SingletonBinding
import org.bitbucket.pshirshov.izumi.di.model.DIKey
import org.bitbucket.pshirshov.izumi.di.{Symb, Tag}

case class TrivialDIDef(bindings: Seq[Def]) extends DIDef

object TrivialDIDef {

  def symbolDef[T: Tag]: ImplDef = ImplDef.TypeImpl(typeSymbol)

  def typeSymbol[T: Tag]: Symb = {
    import scala.reflect.runtime.universe._
    typeTag[T].tpe.typeSymbol
  }

  class BindingSupport(bindings: Seq[Def]) {
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


    def finish: DIDef = TrivialDIDef(bindings)
  }

  final val empty: BindingSupport = new BindingSupport(Seq.empty)
}