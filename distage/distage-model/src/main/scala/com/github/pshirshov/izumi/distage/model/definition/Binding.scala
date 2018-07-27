package com.github.pshirshov.izumi.distage.model.definition

import com.github.pshirshov.izumi.distage.model.providers.ProviderMagnet
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse._
import com.github.pshirshov.izumi.fundamentals.platform.jvm.SourceFilePosition
import com.github.pshirshov.izumi.fundamentals.reflection.CodePositionMaterializer

sealed trait Binding {
  def key: DIKey
  def tags: Set[String]
  def origin: SourceFilePosition
}

object Binding {

  type Aux[+K <: DIKey] = Binding { def key: K }

  sealed trait ImplBinding extends Binding {
    def implementation: ImplDef
  }

  sealed trait SetBinding extends Binding

  // tag equals breaks DSL a little bit (see comment in "Tags in different modules are merged" in InjectorTest)
  final case class SingletonBinding[+K <: DIKey](key: K, implementation: ImplDef, tags: Set[String], origin: SourceFilePosition) extends ImplBinding {
    override def equals(obj: scala.Any): Boolean = obj match {
      case that: SingletonBinding[_] =>
        key == that.key && implementation == that.implementation
      case _ =>
        false
    }

    override val hashCode: Int = (0, key, implementation).hashCode()
  }

  object SingletonBinding {
    def apply[K <: DIKey](key: K, implementation: ImplDef, tags: Set[String] = Set.empty)(implicit pos: CodePositionMaterializer): SingletonBinding[K] =
      new SingletonBinding[K](key, implementation, tags, pos.get.position)
  }

  final case class SetElementBinding[+K <: DIKey](key: K, implementation: ImplDef, tags: Set[String], origin: SourceFilePosition) extends ImplBinding with SetBinding {
    override def equals(obj: scala.Any): Boolean = obj match {
      case that: SetElementBinding[_] =>
        key == that.key && implementation == that.implementation
      case _ =>
        false
    }

    override val hashCode: Int = (1, key, implementation).hashCode()
  }

  object SetElementBinding {
    def apply[K <: DIKey](key: K, implementation: ImplDef, tags: Set[String] = Set.empty)(implicit pos: CodePositionMaterializer): SetElementBinding[K] =
      new SetElementBinding[K](key, implementation, tags, pos.get.position)
  }

  final case class EmptySetBinding[+K <: DIKey](key: K, tags: Set[String], origin: SourceFilePosition) extends SetBinding {
    override def equals(obj: scala.Any): Boolean = obj match {
      case that: EmptySetBinding[_] =>
        key == that.key
      case _ =>
        false
    }

    override val hashCode: Int = (2, key).hashCode()
  }

  object EmptySetBinding {
    def apply[K <: DIKey](key: K, tags: Set[String] = Set.empty)(implicit pos: CodePositionMaterializer): EmptySetBinding[K] =
      new EmptySetBinding[K](key, tags, pos.get.position)
  }

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

    def withImpl[T: Tag](f: ProviderMagnet[T]): ImplBinding =
      withImpl(ImplDef.ProviderImpl(f.get.ret, f.get))

    def withImpl(impl: ImplDef): ImplBinding =
      binding match {
        case b: SingletonBinding[_] =>
          b.copy(implementation = impl)
        case b: SetElementBinding[_] =>
          b.copy(implementation = impl)
      }
  }

  implicit final class WithTags(private val binding: Binding) extends AnyVal {
    def addTags(tags: Set[String]): Binding =
      binding.withTags(binding.tags ++ tags)

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

