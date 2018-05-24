package com.github.pshirshov.izumi.distage.model.definition

import com.github.pshirshov.izumi.distage.model.definition.Binding.{EmptySetBinding, SetElementBinding, SingletonBinding}
import com.github.pshirshov.izumi.distage.model.definition.ModuleDef.{BindDSL, IdentSet, SetDSL}
import com.github.pshirshov.izumi.distage.model.providers.ProviderMagnet
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse._
import com.github.pshirshov.izumi.fundamentals.platform.language.Quirks._

import scala.collection.mutable

trait ModuleDef extends ModuleBase {

  protected def initialState: mutable.Set[Binding] = mutable.HashSet.empty[Binding]

  protected def freeze(state: mutable.Set[Binding]): Set[Binding] = state.toSet

  final private[this] val mutableState: mutable.Set[Binding] = initialState

  final override def bindings: Set[Binding] = freeze(mutableState)

  final protected def make[T: Tag]: BindDSL[T] = {
    val binding = Bindings.binding[T]
    val uniq = mutableState.add(binding)

    new BindDSL(mutableState, binding, uniq)
  }

  final protected def many[T: Tag]: SetDSL[T] = {
    val binding = Bindings.emptySet[T]
    val uniq = mutableState.add(binding)

    val startingSet: Set[Binding] = if (uniq) Set(binding) else Set.empty

    new SetDSL(mutableState, IdentSet(binding.key, Set()), startingSet)
  }

  final protected def append(that: ModuleBase): Unit = discard {
    mutableState ++= that.bindings
  }
}

object ModuleDef {

  // DSL state machine...

  // .bind{.as, .provider}{.named}

  final class BindDSL[T]
  (
    protected val mutableState: mutable.Set[Binding]
    , protected val binding: SingletonBinding[DIKey.TypeKey]
    , protected val ownBinding: Boolean
  ) extends BindDSLMutBase[T] {

    def named(name: String): BindNamedDSL[T] =
      replace(binding.copy(key = binding.key.named(name))) {
        new BindNamedDSL[T](mutableState, _, _)
      }

    def tagged(tags: String*): BindDSL[T] =
      replace(binding.copy(tags = binding.tags ++ tags)) {
        new BindDSL[T](mutableState, _, _)
      }

  }

  final class BindNamedDSL[T]
  (
    protected val mutableState: mutable.Set[Binding]
    , protected val binding: Binding.SingletonBinding[DIKey]
    , protected val ownBinding: Boolean
  ) extends BindDSLMutBase[T] {

    def tagged(tags: String*): BindNamedDSL[T] =
      replace(binding.copy(tags = binding.tags ++ tags)) {
        new BindNamedDSL[T](mutableState, _, _)
      }

  }

  sealed trait BindDSLMutBase[T] extends BindDSLBase[T, Unit] {
    protected def mutableState: mutable.Set[Binding]

    protected val binding: SingletonBinding[DIKey]
    protected val ownBinding: Boolean

    protected def replace[B <: Binding, S](newBinding: B)(newState: (B, Boolean) => S): S = {
      if (ownBinding) {
        mutableState -= binding
      }

      val uniq = mutableState.add(newBinding)

      newState(newBinding, uniq)
    }

    override protected def bind(impl: ImplDef): Unit =
      replace(binding.withImpl(impl)) {
        (_, _) => ()
      }
  }

  // .set{.element, .elementProvider}{.named}

  final case class IdentSet[+D <: DIKey](key: D, tags: Set[String]) {
    def sameIdent(binding: Binding): Boolean =
      key == binding.key && tags == binding.tags
  }

  final class SetDSL[T]
  (
    protected val mutableState: mutable.Set[Binding]
    , protected val identifier: IdentSet[DIKey.TypeKey]
    , protected val currentBindings: Set[Binding]
  ) extends SetDSLMutBase[T] {

    def named(name: String): SetNamedDSL[T] =
      replaceIdent(identifier.copy(key = identifier.key.named(name))) {
        new SetNamedDSL(mutableState, _, _)
      }

    def tagged(tags: String*): SetDSL[T] =
      replaceIdent(identifier.copy(tags = identifier.tags ++ tags)) {
        new SetDSL[T](mutableState, _, _)
      }

  }

  final class SetNamedDSL[T]
  (
    protected val mutableState: mutable.Set[Binding]
    , protected val identifier: IdentSet[DIKey]
    , protected val currentBindings: Set[Binding]
  ) extends SetDSLMutBase[T] {

    def tagged(tags: String*): SetNamedDSL[T] =
      replaceIdent(identifier.copy(tags = identifier.tags ++ tags)) {
        new SetNamedDSL[T](mutableState, _, _)
      }

  }

  final class SetElementDSL[T]
  (
    protected val mutableState: mutable.Set[Binding]
    , protected val identifier: IdentSet[DIKey]
    , protected val currentBindings: Set[Binding]
    , protected val bindingCursor: Binding
  ) extends SetElementDSLMutBase[T] {

    def tagged(tags: String*): SetElementDSL[T] =
      replaceCursor(bindingCursor.withTags(tags = bindingCursor.tags ++ tags))

  }

  sealed trait SetElementDSLMutBase[T] extends SetDSLMutBase[T] {
    protected def bindingCursor: Binding

    protected def replaceCursor(newBindingCursor: Binding): SetElementDSL[T] = {
      mutableState -= bindingCursor
      val newCurrentBindings = currentBindings - bindingCursor

      append(newBindingCursor)

      new SetElementDSL[T](mutableState, identifier, newCurrentBindings + newBindingCursor, newBindingCursor)
    }
  }

  sealed trait SetDSLMutBase[T] extends SetDSLBase[T, SetElementDSL[T]] {
    protected def mutableState: mutable.Set[Binding]

    protected def identifier: IdentSet[DIKey]

    protected def currentBindings: Set[Binding]

    protected def append(binding: Binding): Unit = discard {
      mutableState += binding
    }

    protected def replaceIdent[D <: IdentSet[DIKey], S](newIdent: D)(nextState: (D, Set[Binding]) => S): S = {
      val newBindings = (currentBindings + EmptySetBinding(newIdent.key, newIdent.tags)).map {
        _.withTarget(newIdent.key) // tags only apply to EmptySets
      }

      mutableState --= currentBindings
      mutableState ++= newBindings

      nextState(newIdent, newBindings)
    }

    override protected def appendElement(newElement: ImplDef): SetElementDSL[T] = {
      val newBinding: Binding = SetElementBinding(identifier.key, newElement)

      append(newBinding)

      new SetElementDSL[T](mutableState, identifier, currentBindings + newBinding, newBinding)
    }
  }

  // base

  trait BindDSLBase[T, AfterBind] {
    final def from[I <: T : Tag]: AfterBind =
      bind(ImplDef.TypeImpl(SafeType.get[I]))

    final def from[I <: T : Tag](instance: I): AfterBind =
      bind(ImplDef.InstanceImpl(SafeType.get[I], instance))

    final def from[I <: T : Tag](f: ProviderMagnet[I]): AfterBind =
      bind(ImplDef.ProviderImpl(SafeType.get[I], f.get))

    final def using[I <: T : Tag]: AfterBind =
      bind(ImplDef.ReferenceImpl(SafeType.get[I], DIKey.get[I]))

    final def using[I <: T : Tag](name: String): AfterBind =
      bind(ImplDef.ReferenceImpl(SafeType.get[I], DIKey.get[I].named(name)))

    protected def bind(impl: ImplDef): AfterBind
  }

  trait SetDSLBase[T, AfterAdd] {
    final def ref[I <: T : Tag]: AfterAdd =
      appendElement(ImplDef.ReferenceImpl(SafeType.get[I], DIKey.get[I]))

    final def ref[I <: T : Tag](name: String): AfterAdd =
      appendElement(ImplDef.ReferenceImpl(SafeType.get[I], DIKey.get[I].named(name)))

    final def add[I <: T : Tag]: AfterAdd =
      appendElement(ImplDef.TypeImpl(SafeType.get[I]))

    final def add[I <: T : Tag](instance: I): AfterAdd =
      appendElement(ImplDef.InstanceImpl(SafeType.get[I], instance))

    final def add[I <: T : Tag](f: ProviderMagnet[I]): AfterAdd =
      appendElement(ImplDef.ProviderImpl(f.get.ret, f.get))

    protected def appendElement(newImpl: ImplDef): AfterAdd

    protected def identifier: IdentSet[DIKey]
  }

}
