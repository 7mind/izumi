package com.github.pshirshov.izumi.distage.model.definition

import com.github.pshirshov.izumi.distage.model.functions.WrappedFunction
import com.github.pshirshov.izumi.distage.model.functions.WrappedFunction.DIKeyWrappedFunction
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse._

sealed trait Binding {
  def target: DIKey
}

object Binding {

  type Aux[+T <: DIKey] = Binding { def target: T }

  sealed trait ImplBinding extends Binding {
    def implementation: ImplDef
  }

  final case class SingletonBinding[+T <: DIKey](target: T, implementation: ImplDef) extends ImplBinding

  final case class SetBinding[+T <: DIKey](target: T, implementation: ImplDef) extends ImplBinding

  // Do we need this?
  final case class EmptySetBinding[+T <: DIKey](target: T) extends Binding

  implicit final class WithTarget(private val binding: Binding) extends AnyVal {
    def withTarget[G <: DIKey](newTarget: G): Binding.Aux[G] =
      binding match {
        case b: SingletonBinding[_] =>
          b.copy(target = newTarget)
        case b: SetBinding[_] =>
          b.copy(target = newTarget)
        case b: EmptySetBinding[_] =>
          b.copy(target = newTarget)
      }
  }

  implicit final class WithNamedTarget(private val binding: Binding.Aux[DIKey.TypeKey]) extends AnyVal {
    def named[I: u.Liftable](id: I): Binding.Aux[DIKey.IdKey[I]] = {
      binding.withTarget(binding.target.named(id))
    }
  }

  implicit final class WithImplementation(private val binding: ImplBinding) extends AnyVal {
    def withImpl[T: Tag]: ImplBinding =
      binding match {
        case b: SingletonBinding[_] =>
          b.copy(implementation = ImplDef.TypeImpl(SafeType.get[T]))
        case b: SetBinding[_] =>
          b.copy(implementation = ImplDef.TypeImpl(SafeType.get[T]))
      }

    def withImpl[T: Tag](instance: T): ImplBinding =
      binding match {
        case b: SingletonBinding[_] =>
          b.copy(implementation = ImplDef.InstanceImpl(SafeType.get[T], instance))
        case b: SetBinding[_] =>
          b.copy(implementation = ImplDef.InstanceImpl(SafeType.get[T], instance))
      }

    def withImpl[T: Tag](f: DIKeyWrappedFunction[T]): ImplBinding =
      binding match {
        case b: SingletonBinding[_] =>
          b.copy(implementation = ImplDef.ProviderImpl(f.ret, f))
        case b: SetBinding[_] =>
          b.copy(implementation = ImplDef.ProviderImpl(f.ret, f))
      }
  }
}

