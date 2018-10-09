package com.github.pshirshov.izumi.distage.model.definition

import com.github.pshirshov.izumi.distage.model.providers.ProviderMagnet
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse._
import com.github.pshirshov.izumi.fundamentals.platform.jvm.SourceFilePosition
import com.github.pshirshov.izumi.fundamentals.reflection.CodePositionMaterializer
import com.github.pshirshov.izumi.fundamentals.tags.TagExpr

sealed trait BindingTag extends Any

object BindingTag {

  final case object Untagged extends BindingTag {
    override def toString: String = "<*>"
  }

  final case object TSingleton extends BindingTag {
    override def toString: String = "<singleton>"
  }

  final case object TSet extends BindingTag {
    override def toString: String = "<set>"
  }

  final case object TSetElement extends BindingTag {
    override def toString: String = "<set-element>"
  }

  final case class StringTag(value: String) extends AnyVal with BindingTag {
    override def toString: String = value
  }

  final val untaggedTags: Set[BindingTag] = Set(Untagged)

  def apply(value: String): BindingTag = StringTag(value)

  def apply(tags: String*): Set[BindingTag] = Set(tags.map(BindingTag.apply): _*)

  def fromSeq(tags: Seq[String]): Set[BindingTag] = apply(tags: _*)

  object Expressions extends TagExpr.For[BindingTag] {

    implicit class C(val sc: StringContext) {
      def t(args: Any*): Expr = Has(apply(sc.s(args: _*)))
    }

  }
}

sealed trait Binding {
  def key: DIKey

  def origin: SourceFilePosition

  def tags: Set[BindingTag]

  def withTarget[K <: DIKey](key: K): Binding

  def withTags(tags: Set[BindingTag]): Binding

  def addTags(tags: Set[BindingTag]): Binding
}

object Binding {

  sealed trait ImplBinding extends Binding {
    def implementation: ImplDef

    def withImplDef(implDef: ImplDef): ImplBinding

    override def withTarget[K <: DIKey](key: K): ImplBinding

    override def withTags(tags: Set[BindingTag]): ImplBinding

    override def addTags(tags: Set[BindingTag]): ImplBinding
  }

  sealed trait SetBinding extends Binding

  final case class SingletonBinding[+K <: DIKey](key: K, implementation: ImplDef, _tags: Set[BindingTag], origin: SourceFilePosition) extends ImplBinding {
    override def equals(obj: scala.Any): Boolean = obj match {
      case that: SingletonBinding[_] =>
        key == that.key && implementation == that.implementation
      case _ =>
        false
    }

    override val hashCode: Int = (0, key, implementation).hashCode()

    override def tags: Set[BindingTag] = _tags + BindingTag.TSingleton

    override def withImplDef(implDef: ImplDef): SingletonBinding[K] = copy(implementation = implDef)

    override def withTarget[T <: RuntimeDIUniverse.DIKey](key: T): SingletonBinding[T] = copy(key = key)

    override def withTags(newTags: Set[BindingTag]): SingletonBinding[K] = copy(_tags = newTags)

    override def addTags(moreTags: Set[BindingTag]): SingletonBinding[K] = withTags(this.tags ++ moreTags)
  }

  object SingletonBinding {
    def apply[K <: DIKey](key: K, implementation: ImplDef, tags: Set[BindingTag] = BindingTag.untaggedTags)(implicit pos: CodePositionMaterializer): SingletonBinding[K] =
      new SingletonBinding[K](key, implementation, tags, pos.get.position)
  }

  final case class SetElementBinding[+K <: DIKey](key: K, implementation: ImplDef, _tags: Set[BindingTag], origin: SourceFilePosition) extends ImplBinding with SetBinding {
    override def equals(obj: scala.Any): Boolean = obj match {
      case that: SetElementBinding[_] =>
        key == that.key && implementation == that.implementation
      case _ =>
        false
    }

    override val hashCode: Int = (1, key, implementation).hashCode()

    override def tags: Set[BindingTag] = _tags + BindingTag.TSetElement

    override def withImplDef(implDef: ImplDef): SetElementBinding[K] = copy(implementation = implDef)

    override def withTarget[T <: RuntimeDIUniverse.DIKey](key: T): SetElementBinding[T] = copy(key = key)

    override def withTags(newTags: Set[BindingTag]): SetElementBinding[K] = copy(_tags = newTags)

    override def addTags(moreTags: Set[BindingTag]): SetElementBinding[K] = withTags(this.tags ++ moreTags)
  }

  object SetElementBinding {
    def apply[K <: DIKey](key: K, implementation: ImplDef, tags: Set[BindingTag] = BindingTag.untaggedTags)(implicit pos: CodePositionMaterializer): SetElementBinding[K] =
      new SetElementBinding[K](key, implementation, tags, pos.get.position)
  }

  final case class EmptySetBinding[+K <: DIKey](key: K, _tags: Set[BindingTag], origin: SourceFilePosition) extends SetBinding {
    override def equals(obj: scala.Any): Boolean = obj match {
      case that: EmptySetBinding[_] =>
        key == that.key
      case _ =>
        false
    }

    override val hashCode: Int = (2, key).hashCode()

    override def tags: Set[BindingTag] = _tags + BindingTag.TSet

    override def withTarget[T <: RuntimeDIUniverse.DIKey](key: T): EmptySetBinding[T] = copy(key = key)

    override def withTags(newTags: Set[BindingTag]): EmptySetBinding[K] = copy(_tags = newTags)

    override def addTags(moreTags: Set[BindingTag]): EmptySetBinding[K] = withTags(this.tags ++ moreTags)
  }

  object EmptySetBinding {
    def apply[K <: DIKey](key: K, tags: Set[BindingTag] = BindingTag.untaggedTags)(implicit pos: CodePositionMaterializer): EmptySetBinding[K] =
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

    def withImpl[T: Tag](f: ProviderMagnet[T]): R =
      binding.withImplDef(ImplDef.ProviderImpl(f.get.ret, f.get))
  }

  implicit final class WithTags[R](private val binding: Binding {def withTags(tags: Set[BindingTag]): R}) extends AnyVal {
    def withTags(tags: String*): R =
      binding.withTags(BindingTag.fromSeq(tags))

    def addTags(tags: String*): R =
      binding.withTags(BindingTag.fromSeq(tags))
  }

}

