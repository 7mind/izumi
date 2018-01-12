package org.bitbucket.pshirshov.izumi.di.definition

import org.bitbucket.pshirshov.izumi.di.Tag
import org.bitbucket.pshirshov.izumi.di.definition.Def.SingletonBinding
import org.bitbucket.pshirshov.izumi.di.model.DIKey
import scala.reflect.runtime.universe._

object BasicBindingDsl {
  val start: Seq[Def] = Seq.empty

  def symbolDef[T: Tag]: ImplDef = ImplDef.TypeImpl(typeTag[T].tpe.typeSymbol)

  implicit class BindingSupport(bindings: Seq[Def]) {
    def add[T:Tag]: BindingSupport = {
      BindingSupport(bindings :+ SingletonBinding(DIKey.get[T], symbolDef[T]))
    }

    def add[T:Tag, I <: T:Tag]: BindingSupport = {
      BindingSupport(bindings :+ SingletonBinding(DIKey.get[T], symbolDef[I]))
    }

    def add[T:Tag](instance: T): BindingSupport = {
      BindingSupport(bindings :+ SingletonBinding(DIKey.get[T], ImplDef.InstanceImpl(instance)))
    }

    def finish: Seq[Def] = bindings
  }
}
