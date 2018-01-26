package com.github.pshirshov.izumi.distage.definition

import com.github.pshirshov.izumi.distage.model.DIKey

sealed trait BindingT[T] {
  def target: T
}

object Binding {

  type SingletonBinding = SingletonBindingT[DIKey]
  case class SingletonBindingT[T](target: T, implementation: ImplDef) extends BindingT[T]

  object SingletonBinding {
    def apply(target: DIKey, implementation: ImplDef): SingletonBindingT[DIKey] = SingletonBindingT(target, implementation)

    def unapply(arg: SingletonBindingT[DIKey]): Option[(DIKey, ImplDef)] = SingletonBindingT.unapply[DIKey](arg)
  }

  type SetBinding = SetBindingT[DIKey]
  case class SetBindingT[T](target: T, implementation: ImplDef) extends BindingT[T]

  object SetBinding {
    def apply(target: DIKey, implementation: ImplDef): SetBindingT[DIKey] = SetBindingT(target, implementation)

    def unapply(arg: SetBindingT[DIKey]): Option[(DIKey, ImplDef)] = SetBindingT.unapply[DIKey](arg)
  }

  type EmptySetBinding = EmptySetBindingT[DIKey]
  case class EmptySetBindingT[T](target: T) extends BindingT[T]

  object EmptySetBinding {
    def apply(target: DIKey): EmptySetBindingT[DIKey] = EmptySetBindingT(target)

    def unapply(arg: EmptySetBindingT[DIKey]): Option[DIKey] = EmptySetBindingT.unapply[DIKey](arg)
  }

  implicit class WithTarget[T](binding: BindingT[T]) {
    def withTarget[G](newTarget: G): BindingT[G] = {
      binding match {
        case b: SingletonBindingT[T] =>
          b.copy(target = newTarget)
        case b: SetBindingT[T] =>
          b.copy(target = newTarget)
        case b: EmptySetBindingT[T] =>
          b.copy(target = newTarget)
      }
    }
  }
}

