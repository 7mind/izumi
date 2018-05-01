package com.github.pshirshov.izumi.distage.model.definition

import com.github.pshirshov.izumi.distage.model.functions.WrappedFunction.DIKeyWrappedFunction
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse._

sealed trait Binding {
  def key: DIKey
}

object Binding {

  type Aux[+K <: DIKey] = Binding { def key: K }

  sealed trait ImplBinding extends Binding {
    def implementation: ImplDef
  }

  final case class SingletonBinding[+K <: DIKey](key: K, implementation: ImplDef) extends ImplBinding

  final case class SetBinding[+K <: DIKey](key: K, implementation: ImplDef) extends ImplBinding

  // Do we need this?
  final case class EmptySetBinding[+K <: DIKey](key: K) extends Binding

  implicit final class WithTarget(private val binding: Binding) extends AnyVal {
    def withTarget[G <: DIKey](newTarget: G): Binding.Aux[G] =
      binding match {
        case b: SingletonBinding[_] =>
          b.copy(key = newTarget)
        case b: SetBinding[_] =>
          b.copy(key = newTarget)
        case b: EmptySetBinding[_] =>
          b.copy(key = newTarget)
      }
  }

  implicit final class WithNamedTarget(private val binding: Binding.Aux[DIKey.TypeKey]) extends AnyVal {
    def named[I: u.Liftable](id: I): Binding.Aux[DIKey.IdKey[I]] = {
      binding.withTarget(binding.key.named(id))
    }
  }

  implicit final class WithImplementation(private val binding: ImplBinding) extends AnyVal {
    def withImpl[T: Tag]: ImplBinding =
      withImpl(ImplDef.TypeImpl(SafeType.get[T]))

    def withImpl[T: Tag](instance: T): ImplBinding =
      withImpl(ImplDef.InstanceImpl(SafeType.get[T], instance))

    def withImpl[T: Tag](f: DIKeyWrappedFunction[T]): ImplBinding =
      withImpl(ImplDef.ProviderImpl(f.ret, f))

    def withImpl(impl: ImplDef): ImplBinding =
      binding match {
        case b: SingletonBinding[_] =>
          b.copy(implementation = impl)
        case b: SetBinding[_] =>
          b.copy(implementation = impl)
      }
  }
}

