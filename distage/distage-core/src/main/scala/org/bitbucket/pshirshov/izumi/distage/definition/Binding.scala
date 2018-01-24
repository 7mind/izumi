package org.bitbucket.pshirshov.izumi.distage.definition

import org.bitbucket.pshirshov.izumi.distage.model.DIKey

sealed trait BindingT[T] {
  def target: T
  def setTarget[G]: G => BindingT[G]
}

object Binding {
  type SingletonBinding = SingletonBindingT[DIKey]
  case class SingletonBindingT[T](target: T, implementation: ImplDef) extends BindingT[T] {
    override def setTarget[G]: G => BindingT[G] = copy(_)
  }
  object SingletonBinding {
    def apply(target: DIKey, implementation: ImplDef): SingletonBindingT[DIKey] = SingletonBindingT(target, implementation)

    def unapply(arg: SingletonBindingT[DIKey]): Option[(DIKey, ImplDef)] = SingletonBindingT.unapply[DIKey](arg)
  }

  type SetBinding = SetBindingT[DIKey]
  case class SetBindingT[T](target: T, implementation: ImplDef) extends BindingT[T] {
    override def setTarget[G]: G => BindingT[G] = copy(_)
  }
  object SetBinding {
    def apply(target: DIKey, implementation: ImplDef): SetBindingT[DIKey] = new SetBindingT(target, implementation)

    def unapply(arg: SetBindingT[DIKey]): Option[(DIKey, ImplDef)] = SetBindingT.unapply[DIKey](arg)
  }

  type EmptySetBinding = EmptySetBindingT[DIKey]
  case class EmptySetBindingT[T](target: T) extends BindingT[T] {
    override def setTarget[G]: G => BindingT[G] = copy(_)
  }
  object EmptySetBinding {
    def apply(target: DIKey): EmptySetBindingT[DIKey] = new EmptySetBindingT(target)

    def unapply(arg: EmptySetBindingT[DIKey]): Option[DIKey] = EmptySetBindingT.unapply[DIKey](arg)
  }
}

