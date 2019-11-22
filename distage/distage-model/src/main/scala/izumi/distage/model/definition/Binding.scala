package izumi.distage.model.definition

import izumi.distage.constructors.AnyConstructor
import izumi.distage.model.definition.Binding.GroupingKey
import izumi.distage.model.providers.ProviderMagnet
import izumi.distage.model.reflection.universe.RuntimeDIUniverse
import izumi.distage.model.reflection.universe.RuntimeDIUniverse._
import izumi.fundamentals.platform.language.SourceFilePosition

sealed trait Binding {
  def key: DIKey
  def origin: SourceFilePosition
  def tags: Set[BindingTag]

  def group: GroupingKey

  def addTags(tags: Set[BindingTag]): Binding
  protected[this] def withTags(tags: Set[BindingTag]): Binding

  override final def hashCode(): Int = group.hashCode()
  override final def equals(obj: Any): Boolean = obj match {
    case b: Binding =>
      b.group == this.group && b.tags == this.tags
    case _ =>
      false
  }
}

object Binding {

  sealed trait GroupingKey {
    def key: DIKey
  }
  object GroupingKey {
    final case class KeyImpl(key: DIKey, impl: ImplDef) extends GroupingKey
    final case class Key(key: DIKey) extends GroupingKey
  }

  private[Binding] sealed trait WithTarget {
    def withTarget[K <: DIKey](key: K): Binding
  }
  sealed trait ImplBinding extends Binding {
    def implementation: ImplDef

    def withImplDef(implDef: ImplDef): ImplBinding
    protected[this] def withTags(tags: Set[BindingTag]): ImplBinding
    override def addTags(tags: Set[BindingTag]): ImplBinding
  }

  sealed trait SetBinding extends Binding

  final case class SingletonBinding[+K <: DIKey](key: K, implementation: ImplDef, tags: Set[BindingTag], origin: SourceFilePosition) extends ImplBinding with WithTarget {
    override def group: GroupingKey = GroupingKey.KeyImpl(key, implementation)

    override def withImplDef(implDef: ImplDef): SingletonBinding[K] = copy(implementation = implDef)
    override def withTarget[T <: RuntimeDIUniverse.DIKey](key: T): SingletonBinding[T] = copy(key = key)
    protected[this] def withTags(newTags: Set[BindingTag]): SingletonBinding[K] = copy(tags = newTags)
    override def addTags(moreTags: Set[BindingTag]): SingletonBinding[K] = withTags(this.tags ++ moreTags)
  }

  final case class SetElementBinding(key: DIKey.SetElementKey, implementation: ImplDef, tags: Set[BindingTag], origin: SourceFilePosition) extends ImplBinding with SetBinding {
    override def group: GroupingKey = GroupingKey.KeyImpl(key, implementation)
    override def withImplDef(implDef: ImplDef): SetElementBinding = copy(implementation = implDef)
    protected[this] def withTags(newTags: Set[BindingTag]): SetElementBinding = copy(tags = newTags)
    override def addTags(moreTags: Set[BindingTag]): SetElementBinding = withTags(this.tags ++ moreTags)
  }

  final case class EmptySetBinding[+K <: DIKey](key: K, tags: Set[BindingTag], origin: SourceFilePosition) extends SetBinding with WithTarget {
    override def group: GroupingKey = GroupingKey.Key(key)
    override def withTarget[T <: RuntimeDIUniverse.DIKey](key: T): EmptySetBinding[T] = copy(key = key)
    protected[this] def withTags(newTags: Set[BindingTag]): EmptySetBinding[K] = copy(tags = newTags)
    override def addTags(moreTags: Set[BindingTag]): EmptySetBinding[K] = withTags(this.tags ++ moreTags)
  }

  implicit final class WithImplementation[R](private val binding: ImplBinding {def withImplDef(implDef: ImplDef): R}) extends AnyVal {
    def withImpl[T: Tag: AnyConstructor]: R =
      withImpl[T](AnyConstructor[T].provider)

    def withImpl[T: Tag](instance: T): R =
      binding.withImplDef(ImplDef.InstanceImpl(SafeType.get[T], instance))

    def withImpl[T: Tag](function: ProviderMagnet[T]): R =
      binding.withImplDef(ImplDef.ProviderImpl(function.get.ret, function.get))
  }

}

