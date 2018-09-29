package com.github.pshirshov.izumi.distage.model.definition

import com.github.pshirshov.izumi.distage.model.providers.ProviderMagnet
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse._
import com.github.pshirshov.izumi.fundamentals.platform.jvm.SourceFilePosition
import com.github.pshirshov.izumi.fundamentals.reflection.CodePositionMaterializer

sealed trait Binding {
  def key: DIKey
  def tags: Set[String]
  def origin: SourceFilePosition

  def withTarget[K <: DIKey](key: K): Binding
  def withTags(tags: Set[String]): Binding
  def addTags(tags: Set[String]): Binding
}

object Binding {

  sealed trait ImplBinding extends Binding {
    def implementation: ImplDef
    def withImplDef(implDef: ImplDef): ImplBinding

    override def withTarget[K <: DIKey](key: K): ImplBinding
    override def withTags(tags: Set[String]): ImplBinding
    override def addTags(tags: Set[String]): ImplBinding
  }

  sealed trait SetBinding extends Binding

  final case class SingletonBinding[+K <: DIKey](key: K, implementation: ImplDef, tags: Set[String], origin: SourceFilePosition) extends ImplBinding {
    override def equals(obj: scala.Any): Boolean = obj match {
      case that: SingletonBinding[_] =>
        key == that.key && implementation == that.implementation
      case _ =>
        false
    }

    override val hashCode: Int = (0, key, implementation).hashCode()

    override def withImplDef(implDef: ImplDef): SingletonBinding[K] = copy(implementation = implDef)
    override def withTarget[T <: RuntimeDIUniverse.DIKey](key: T): SingletonBinding[T] = copy(key = key)
    override def withTags(tags: Set[String]): SingletonBinding[K] = copy(tags = tags)
    override def addTags(tags: Set[String]): SingletonBinding[K] = withTags(this.tags ++ tags)
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

    override def withImplDef(implDef: ImplDef): SetElementBinding[K] = copy(implementation = implDef)
    override def withTarget[T <: RuntimeDIUniverse.DIKey](key: T): SetElementBinding[T] = copy(key = key)
    override def withTags(tags: Set[String]): SetElementBinding[K] = copy(tags = tags)
    override def addTags(tags: Set[String]): SetElementBinding[K] = withTags(this.tags ++ tags)
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

    override def withTarget[T <: RuntimeDIUniverse.DIKey](key: T): EmptySetBinding[T] = copy(key = key)
    override def withTags(tags: Set[String]): EmptySetBinding[K] = copy(tags = tags)
    override def addTags(tags: Set[String]): EmptySetBinding[K] = withTags(this.tags ++ tags)
  }

  object EmptySetBinding {
    def apply[K <: DIKey](key: K, tags: Set[String] = Set.empty)(implicit pos: CodePositionMaterializer): EmptySetBinding[K] =
      new EmptySetBinding[K](key, tags, pos.get.position)
  }

  implicit final class WithNamedTarget[R](private val binding: Binding { def key: DIKey.TypeKey ; def withTarget[T <: RuntimeDIUniverse.DIKey](key: T): R } ) extends AnyVal {
    def named[I: IdContract](id: I): R = {
      binding.withTarget(binding.key.named(id))
    }
  }

  implicit final class WithImplementation[R](private val binding: ImplBinding { def withImplDef(implDef: ImplDef): R }) extends AnyVal {
    def withImpl[T: Tag]: R =
      binding.withImplDef(ImplDef.TypeImpl(SafeType.get[T]))

    def withImpl[T: Tag](instance: T): R =
      binding.withImplDef(ImplDef.InstanceImpl(SafeType.get[T], instance))

    def withImpl[T: Tag](f: ProviderMagnet[T]): R =
      binding.withImplDef(ImplDef.ProviderImpl(f.get.ret, f.get))
  }

  implicit final class WithTags[R](private val binding: Binding { def withTags(tags: Set[String]): R }) extends AnyVal {
    def withTags(tags: String*): R =
      binding.withTags(Set(tags: _*))

    def addTags(tags: String*): R =
      binding.withTags(Set(tags: _*))
  }
}

