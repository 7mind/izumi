package com.github.pshirshov.izumi.distage.model.definition

import com.github.pshirshov.izumi.distage.model.definition.Binding.{EmptySetBinding, SetElementBinding, SingletonBinding}
import com.github.pshirshov.izumi.distage.model.definition.ModuleDef.{BindDSL, IdentSet, SetDSL}
import com.github.pshirshov.izumi.distage.model.functions.DIKeyWrappedFunction
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

  private[definition] final class BindDSL[T]
  (
    protected val mutableState: mutable.Set[Binding]
    , protected val binding: SingletonBinding[DIKey.TypeKey]
    , protected val ownBinding: Boolean
  ) extends BindDSLMutBase[T] {

    def named(name: String): BindNamedDSL[T] = {
      val newBinding = binding.copy(key = binding.key.named(name))

      val uniq = replace(newBinding)

      new BindNamedDSL[T](mutableState, newBinding, uniq)
    }

    def tagged(tags: String*): BindDSL[T] = {
      val newBinding = binding.copy(tags = binding.tags ++ tags)

      val uniq = replace(newBinding)

      new BindDSL[T](mutableState, newBinding, uniq)
    }

  }

  private[definition] final class BindNamedDSL[T]
  (
    protected val mutableState: mutable.Set[Binding]
    , protected val binding: Binding.SingletonBinding[DIKey]
    , protected val ownBinding: Boolean
  ) extends BindDSLMutBase[T] {

    def tagged(tags: String*): BindNamedDSL[T] = {
      val newBinding = binding.copy(tags = binding.tags ++ tags)

      val uniq = replace(newBinding)

      new BindNamedDSL[T](mutableState, newBinding, uniq)
    }

  }

  private[definition] sealed trait BindDSLMutBase[T] extends BindDSLBase[T, Unit] {
    protected def mutableState: mutable.Set[Binding]

    protected val binding: SingletonBinding[DIKey]
    protected val ownBinding: Boolean

    protected def replace(newBinding: Binding): Boolean = {
      if (ownBinding) {
        mutableState -= binding
      }
      mutableState.add(newBinding)
    }

    override protected def bind(impl: ImplDef): Unit = discard {
      replace(binding.withImpl(impl))
    }
  }

  // .set{.element, .elementProvider}{.named}

  private[definition] final case class IdentSet[+D <: DIKey](key: D, tags: Set[String]) {
    def sameIdent(binding: Binding): Boolean =
      key == binding.key && tags == binding.tags
  }

  private[definition] final class SetDSL[T]
  (
    protected val mutableState: mutable.Set[Binding]
    , protected val identifier: IdentSet[DIKey.TypeKey]
    , protected val currentBindings: Set[Binding]
  ) extends SetDSLMutBase[T] {

    def named(name: String): SetNamedDSL[T] = {
      val newIdent = identifier.copy(key = identifier.key.named(name))

      val newBindings = replaceIdent(newIdent)

      new SetNamedDSL(mutableState, newIdent, newBindings)
    }

    def tagged(tags: String*): SetDSL[T] = {
      val newIdent = identifier.copy(tags = identifier.tags ++ tags)

      val newBindings = replaceIdent(newIdent)

      new SetDSL[T](mutableState, newIdent, newBindings)
    }

  }

  private[definition] final class SetNamedDSL[T]
  (
    protected val mutableState: mutable.Set[Binding]
    , protected val identifier: IdentSet[DIKey]
    , protected val currentBindings: Set[Binding]
  ) extends SetDSLMutBase[T] {

    def tagged(tags: String*): SetNamedDSL[T] = {
      val newIdent = identifier.copy(tags = identifier.tags ++ tags)

      val newBindings = replaceIdent(newIdent)

      new SetNamedDSL[T](mutableState, newIdent, newBindings)
    }

  }

  private[definition] final class SetElementDSL[T]
  (
    protected val mutableState: mutable.Set[Binding]
    , protected val identifier: IdentSet[DIKey]
    , protected val currentBindings: Set[Binding]
    , protected val bindingCursor: Binding
  ) extends SetDSLMutBase[T] {

    def tagged(tags: String*): SetElementDSL[T] = {
      val newBindingCursor = bindingCursor.withTags(tags = bindingCursor.tags ++ tags)

      mutableState -= bindingCursor
      val newCurrentBindings = currentBindings - bindingCursor

      append(newBindingCursor)

      new SetElementDSL[T](mutableState, identifier, newCurrentBindings + newBindingCursor, newBindingCursor)
    }

  }

  private[definition] sealed trait SetDSLMutBase[T] extends SetDSLBase[T, SetElementDSL[T]] {
    protected def mutableState: mutable.Set[Binding]

    protected def identifier: IdentSet[DIKey]

    protected def currentBindings: Set[Binding]

    protected def append(binding: Binding): Unit = discard {
      mutableState += binding
    }

    protected def replaceIdent(newIdent: IdentSet[DIKey]): Set[Binding] = {
      val newBindings = (currentBindings + EmptySetBinding(newIdent.key, newIdent.tags)).map {
        _.withTarget(newIdent.key) // tags only apply to EmptySet
      }

      mutableState --= currentBindings
      mutableState ++= newBindings

      newBindings
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

    final def from[I <: T : Tag](f: DIKeyWrappedFunction[I]): AfterBind =
      bind(ImplDef.ProviderImpl(SafeType.get[I], f))

    final def using[I <: T : Tag]: AfterBind =
      bind(ImplDef.ReferenceImpl(SafeType.get[I], Bindings.binding[I].key))

    final def using[I <: T : Tag](name: String): AfterBind =
      bind(ImplDef.ReferenceImpl(SafeType.get[I], Bindings.binding[I].named(name).key))

    protected def bind(impl: ImplDef): AfterBind
  }

  trait SetDSLBase[T, AfterAdd] {
    // TODO: maybe this needs to be cleaned/improved
    final def ref[I <: T : Tag]: AfterAdd =
      appendElement(ImplDef.ReferenceImpl(SafeType.get[I], Bindings.binding[I].key))

    // TODO: shitty
    final def ref[I <: T : Tag](name: String): AfterAdd =
      appendElement(ImplDef.ReferenceImpl(SafeType.get[I], Bindings.binding[I].named(name).key))


    final def add[I <: T : Tag]: AfterAdd =
      appendElement(ImplDef.TypeImpl(SafeType.get[I]))

    final def add[I <: T : Tag](instance: I): AfterAdd =
      appendElement(ImplDef.InstanceImpl(SafeType.get[I], instance))

    final def add[I <: T : Tag](f: DIKeyWrappedFunction[I]): AfterAdd =
      appendElement(ImplDef.ProviderImpl(f.ret, f))

    protected def appendElement(newImpl: ImplDef): AfterAdd

    protected def identifier: IdentSet[DIKey]
  }

}
