package com.github.pshirshov.izumi.distage.model.definition

import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeUniverse

sealed trait BindingT[+T] {
  def target: T
}

object BindingT {

  type SingletonBinding = SingletonBindingT[RuntimeUniverse.DIKey]
  case class SingletonBindingT[T](target: T, implementation: ImplDef) extends BindingT[T]

  object SingletonBinding {
    def apply(target: RuntimeUniverse.DIKey, implementation: ImplDef): SingletonBindingT[RuntimeUniverse.DIKey] = SingletonBindingT(target, implementation)

    def unapply(arg: SingletonBindingT[RuntimeUniverse.DIKey]): Option[(RuntimeUniverse.DIKey, ImplDef)] = SingletonBindingT.unapply[RuntimeUniverse.DIKey](arg)
  }

  type SetBinding = SetBindingT[RuntimeUniverse.DIKey]
  case class SetBindingT[T](target: T, implementation: ImplDef) extends BindingT[T]

  object SetBinding {
    def apply(target: RuntimeUniverse.DIKey, implementation: ImplDef): SetBindingT[RuntimeUniverse.DIKey] = SetBindingT(target, implementation)

    def unapply(arg: SetBindingT[RuntimeUniverse.DIKey]): Option[(RuntimeUniverse.DIKey, ImplDef)] = SetBindingT.unapply[RuntimeUniverse.DIKey](arg)
  }

  type EmptySetBinding = EmptySetBindingT[RuntimeUniverse.DIKey]
  case class EmptySetBindingT[T](target: T) extends BindingT[T]

  object EmptySetBinding {
    def apply(target: RuntimeUniverse.DIKey): EmptySetBindingT[RuntimeUniverse.DIKey] = EmptySetBindingT(target)

    def unapply(arg: EmptySetBindingT[RuntimeUniverse.DIKey]): Option[RuntimeUniverse.DIKey] = EmptySetBindingT.unapply[RuntimeUniverse.DIKey](arg)
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

