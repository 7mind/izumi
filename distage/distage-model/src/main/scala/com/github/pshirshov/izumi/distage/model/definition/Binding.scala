package com.github.pshirshov.izumi.distage.model.definition

import com.github.pshirshov.izumi.distage.model.definition.Binding.GroupingKey
import com.github.pshirshov.izumi.distage.model.providers.ProviderMagnet
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse._
import com.github.pshirshov.izumi.fundamentals.platform.jvm.SourceFilePosition
import com.github.pshirshov.izumi.fundamentals.reflection.CodePositionMaterializer

sealed trait Binding {

  def key: DIKey

  def origin: SourceFilePosition

  def group: GroupingKey
  def tags: Set[BindingTag]

  def withTarget[K <: DIKey](key: K): Binding
  def addTags(tags: Set[BindingTag]): Binding

  protected[distage] def withTags(tags: Set[BindingTag]): Binding
}

object Binding {
  sealed trait GroupingKey {
    def key: DIKey
  }
  object GroupingKey {
    case class KeyImpl(key: DIKey, impl: ImplDef) extends GroupingKey
    case class Key(key: DIKey) extends GroupingKey
  }

  sealed trait ImplBinding extends Binding {
    def implementation: ImplDef

    def withImplDef(implDef: ImplDef): ImplBinding
    override def withTarget[K <: DIKey](key: K): ImplBinding
    protected[distage] def withTags(tags: Set[BindingTag]): ImplBinding
    override def addTags(tags: Set[BindingTag]): ImplBinding
  }

  sealed trait SetBinding extends Binding

  final case class SingletonBinding[+K <: DIKey](key: K, implementation: ImplDef, tags: Set[BindingTag], origin: SourceFilePosition) extends ImplBinding {
    override def group: GroupingKey = GroupingKey.KeyImpl(key, implementation)

    override def withImplDef(implDef: ImplDef): SingletonBinding[K] = copy(implementation = implDef)
    override def withTarget[T <: RuntimeDIUniverse.DIKey](key: T): SingletonBinding[T] = copy(key = key)
    protected[distage] def withTags(newTags: Set[BindingTag]): SingletonBinding[K] = copy(tags = newTags)
    override def addTags(moreTags: Set[BindingTag]): SingletonBinding[K] = withTags(this.tags ++ moreTags)
  }

  object SingletonBinding {
    def apply[K <: DIKey](key: K, implementation: ImplDef, tags: Set[BindingTag] = Set.empty)(implicit pos: CodePositionMaterializer): SingletonBinding[K] =
      new SingletonBinding[K](key, implementation, tags, pos.get.position)
  }

  final case class SetElementBinding[+K <: DIKey](key: K, implementation: ImplDef, tags: Set[BindingTag], origin: SourceFilePosition) extends ImplBinding with SetBinding {
    override def group: GroupingKey = GroupingKey.KeyImpl(key, implementation)
    override def withImplDef(implDef: ImplDef): SetElementBinding[K] = copy(implementation = implDef)
    override def withTarget[T <: RuntimeDIUniverse.DIKey](key: T): SetElementBinding[T] = copy(key = key)
    protected[distage] def withTags(newTags: Set[BindingTag]): SetElementBinding[K] = copy(tags = newTags)
    override def addTags(moreTags: Set[BindingTag]): SetElementBinding[K] = withTags(this.tags ++ moreTags)
  }

  object SetElementBinding {
    def apply[K <: DIKey](key: K, implementation: ImplDef, tags: Set[BindingTag] = Set.empty)(implicit pos: CodePositionMaterializer): SetElementBinding[K] =
      new SetElementBinding[K](key, implementation, tags, pos.get.position)
  }

  final case class EmptySetBinding[+K <: DIKey](key: K, tags: Set[BindingTag], origin: SourceFilePosition) extends SetBinding {
    override def group: GroupingKey = GroupingKey.Key(key)
    override def withTarget[T <: RuntimeDIUniverse.DIKey](key: T): EmptySetBinding[T] = copy(key = key)
    protected[distage] def withTags(newTags: Set[BindingTag]): EmptySetBinding[K] = copy(tags = newTags)
    override def addTags(moreTags: Set[BindingTag]): EmptySetBinding[K] = withTags(this.tags ++ moreTags)
  }

  object EmptySetBinding {
    def apply[K <: DIKey](key: K, tags: Set[BindingTag] = Set.empty)(implicit pos: CodePositionMaterializer): EmptySetBinding[K] =
      new EmptySetBinding[K](key, tags, pos.get.position)
  }

  implicit final class WithNamedTarget[R](private val binding: Binding {def key: DIKey.TypeKey; def withTarget[T <: RuntimeDIUniverse.DIKey](key: T): R}) extends AnyVal {
    def named[I: IdContract](id: I): R = {
      binding.withTarget(binding.key.named(id))
    }
  }

  implicit final class WithImplementation[R](private val binding: ImplBinding {def withImplDef(implDef: ImplDef): R}) extends AnyVal {
    def withImpl[T: Tag]: R =
      binding.withImplDef(ImplDef.TypeImpl(SafeType.get[T]))

    def withImpl[T: Tag](instance: T): R =
      binding.withImplDef(ImplDef.InstanceImpl(SafeType.get[T], instance))

    def withImpl[T: Tag](function: ProviderMagnet[T]): R =
      binding.withImplDef(ImplDef.ProviderImpl(function.get.ret, function.get))
  }

}

