package com.github.pshirshov.izumi.distage.model.definition

import com.github.pshirshov.izumi.distage.model.providers.DIKeyWrappedFunction
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse._

sealed trait Binding {
  def key: DIKey
  def tags: Set[String]
}

object Binding {

  type Aux[+K <: DIKey] = Binding { def key: K }

  sealed trait ImplBinding extends Binding {
    def implementation: ImplDef
  }

  final case class SingletonBinding[+K <: DIKey](key: K, implementation: ImplDef, tags: Set[String] = Set.empty) extends ImplBinding

  sealed trait SetBinding extends Binding

  final case class SetElementBinding[+K <: DIKey](key: K, implementation: ImplDef, tags: Set[String] = Set.empty) extends ImplBinding with SetBinding

  // Do we need this? - we do, we may wish to define an empty set. Without elements
  final case class EmptySetBinding[+K <: DIKey](key: K, tags: Set[String] = Set.empty) extends SetBinding

  implicit final class WithTarget(private val binding: Binding) extends AnyVal {
    def withTarget[G <: DIKey](newTarget: G): Binding.Aux[G] =
      binding match {
        case b: SingletonBinding[_] =>
          b.copy(key = newTarget)
        case b: SetElementBinding[_] =>
          b.copy(key = newTarget)
        case b: EmptySetBinding[_] =>
          b.copy(key = newTarget)
      }
  }

  implicit final class WithNamedTarget(private val binding: Binding.Aux[DIKey.TypeKey]) extends AnyVal {
    def named[I: IdContract](id: I): Binding.Aux[DIKey.IdKey[I]] = {
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
        case b: SetElementBinding[_] =>
          b.copy(implementation = impl)
      }
  }

  implicit final class WithTags(private val binding: Binding) extends AnyVal {
    def withTags(tags: Set[String]): Binding =
      binding match {
        case b: SingletonBinding[_] =>
          b.copy(tags = tags)
        case b: SetElementBinding[_] =>
          b.copy(tags = tags)
        case b: EmptySetBinding[_] =>
          b.copy(tags = tags)
      }

    def withTags(tags: String*): Binding =
      withTags(Set(tags: _*))
  }
}

