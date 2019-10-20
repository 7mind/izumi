package izumi.distage.model.definition

import izumi.distage.model.definition.Binding.GroupingKey
import izumi.distage.model.providers.ProviderMagnet
import izumi.distage.model.reflection
import izumi.distage.model.reflection.universe
import izumi.distage.model.reflection.universe.RuntimeDIUniverse
import izumi.distage.model.reflection.universe.RuntimeDIUniverse._
import izumi.fundamentals.platform.jvm.SourceFilePosition

sealed trait Binding {

  def key: DIKey

  def origin: SourceFilePosition

  def group: GroupingKey
  def tags: Set[BindingTag]

  def withTarget[K <: DIKey](key: K): Binding
  def addTags(tags: Set[BindingTag]): Binding


  override def hashCode(): Int = group.hashCode()

  override def equals(obj: Any): Boolean = obj match {
    case b: Binding =>
      b.group == this.group && b.tags == this.tags
    case _ =>
      false
  }

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

  final case class SetElementBinding(key: DIKey.SetElementKey, implementation: ImplDef, tags: Set[BindingTag], origin: SourceFilePosition) extends ImplBinding with SetBinding {
    override lazy val group: GroupingKey = {
      def fixSetKey(k: DIKey.SetElementKey): DIKey.SetElementKey = {
        k.reference match {
          case id: DIKey.IdKey[_] =>
            DIKey.SetElementKey(key.set, fixKey(id))
          case _ =>
            k
        }
      }
      def fixKey(k: DIKey): reflection.universe.RuntimeDIUniverse.DIKey = {
        k match {
          case id: DIKey.IdKey[_] =>
            id.id match {
              case _: DIKey.SetLocId =>
                DIKey.TypeKey(id.tpe)
              case _ =>
                k
            }
          case _ =>
            k
        }
      }

      val gk = fixSetKey(key)
      GroupingKey.KeyImpl(gk, implementation)
    }
    override def withImplDef(implDef: ImplDef): SetElementBinding = copy(implementation = implDef)
    override def withTarget[T <: RuntimeDIUniverse.DIKey](key: T): SetElementBinding =  {
      // TODO: seems like this will never be invoked
      ???
      copy(key = this.key.copy(reference = key))
    }
    protected[distage] def withTags(newTags: Set[BindingTag]): SetElementBinding = copy(tags = newTags)
    override def addTags(moreTags: Set[BindingTag]): SetElementBinding = withTags(this.tags ++ moreTags)
  }

  final case class EmptySetBinding[+K <: DIKey](key: K, tags: Set[BindingTag], origin: SourceFilePosition) extends SetBinding {
    override def group: GroupingKey = GroupingKey.Key(key)
    override def withTarget[T <: RuntimeDIUniverse.DIKey](key: T): EmptySetBinding[T] = copy(key = key)
    protected[distage] def withTags(newTags: Set[BindingTag]): EmptySetBinding[K] = copy(tags = newTags)
    override def addTags(moreTags: Set[BindingTag]): EmptySetBinding[K] = withTags(this.tags ++ moreTags)
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

