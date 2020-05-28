package izumi.distage.model.definition

import izumi.distage.constructors.AnyConstructor
import izumi.distage.model.definition.Binding.GroupingKey
import izumi.distage.model.plan.repr.{BindingFormatter, KeyFormatter}
import izumi.distage.model.providers.ProviderMagnet
import izumi.distage.model.reflection._
import izumi.fundamentals.platform.language.SourceFilePosition
import izumi.fundamentals.reflection.Tags.Tag

sealed trait Binding {
  def key: DIKey
  def origin: SourceFilePosition
  def tags: Set[BindingTag]

  def group: GroupingKey

  def addTags(tags: Set[BindingTag]): Binding
  def withTags(tags: Set[BindingTag]): Binding

  override final def toString: String = BindingFormatter(KeyFormatter.Full).formatBinding(this)
  override final def hashCode(): Int = group.hashCode
  override final def equals(obj: Any): Boolean = obj match {
    case b: Binding =>
      b.group == this.group && b.tags == this.tags
    case _ =>
      false
  }
}

object Binding {

  sealed trait GroupingKey {
    override final lazy val hashCode: Int = scala.util.hashing.MurmurHash3.productHash(this.asInstanceOf[Product])
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
    override def withTags(tags: Set[BindingTag]): ImplBinding
    override def addTags(tags: Set[BindingTag]): ImplBinding
  }

  final case class SingletonBinding[+K <: DIKey](key: K, implementation: ImplDef, tags: Set[BindingTag], origin: SourceFilePosition, isMutator: Boolean = false)
    extends ImplBinding
    with WithTarget {
    override lazy val group: GroupingKey = GroupingKey.KeyImpl(key, implementation)
    override def withImplDef(implDef: ImplDef): SingletonBinding[K] = copy(implementation = implDef)
    override def withTarget[T <: DIKey](key: T): SingletonBinding[T] = copy(key = key)
    override def withTags(newTags: Set[BindingTag]): SingletonBinding[K] = copy(tags = newTags)
    override def addTags(moreTags: Set[BindingTag]): SingletonBinding[K] = withTags(this.tags ++ moreTags)
  }

  sealed trait SetBinding extends Binding

  final case class SetElementBinding(key: DIKey.SetElementKey, implementation: ImplDef, tags: Set[BindingTag], origin: SourceFilePosition)
    extends ImplBinding
    with SetBinding {
    override lazy val group: GroupingKey = GroupingKey.KeyImpl(key, implementation)
    override def withImplDef(implDef: ImplDef): SetElementBinding = copy(implementation = implDef)
    def withTarget(key: DIKey.SetElementKey): SetElementBinding = copy(key = key)
    override def withTags(newTags: Set[BindingTag]): SetElementBinding = copy(tags = newTags)
    override def addTags(moreTags: Set[BindingTag]): SetElementBinding = withTags(this.tags ++ moreTags)
  }

  final case class EmptySetBinding[+K <: DIKey](key: K, tags: Set[BindingTag], origin: SourceFilePosition) extends SetBinding with WithTarget {
    override lazy val group: GroupingKey = GroupingKey.Key(key)
    override def withTarget[T <: DIKey](key: T): EmptySetBinding[T] = copy(key = key)
    override def withTags(newTags: Set[BindingTag]): EmptySetBinding[K] = copy(tags = newTags)
    override def addTags(moreTags: Set[BindingTag]): EmptySetBinding[K] = withTags(this.tags ++ moreTags)
  }

  implicit final class WithImplementation[R](private val binding: ImplBinding { def withImplDef(implDef: ImplDef): R }) extends AnyVal {
    def withImpl[T: Tag: AnyConstructor]: R =
      withImpl[T](AnyConstructor[T])

    def withImpl[T: Tag](instance: T): R =
      binding.withImplDef(ImplDef.InstanceImpl(SafeType.get[T], instance))

    def withImpl[T: Tag](function: ProviderMagnet[T]): R =
      binding.withImplDef(ImplDef.ProviderImpl(function.get.ret, function.get))
  }

}
