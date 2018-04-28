package com.github.pshirshov.izumi.distage.model.definition

import com.github.pshirshov.izumi.distage.model.definition.Binding.{EmptySetBinding, SetBinding, SingletonBinding}
import com.github.pshirshov.izumi.distage.model.functions.WrappedFunction.DIKeyWrappedFunction
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse

class BindingDSL private[definition] (override val bindings: Set[Binding]) extends ModuleDef {
  import BindingDSL._

  override protected type Impl = BindingDSL

  override protected def make(bindings: Set[Binding]): BindingDSL = new BindingDSL(bindings)

  def bind[T: RuntimeDIUniverse.Tag]: BindDSL[T] =
    new BindDSL(bindings, RuntimeDIUniverse.DIKey.get[T], ImplDef.TypeImpl(RuntimeDIUniverse.SafeType.get[T]))

  def bind[T: RuntimeDIUniverse.Tag](instance: T): BindDSL[T] =
    new BindDSL(bindings, RuntimeDIUniverse.DIKey.get[T], ImplDef.InstanceImpl(RuntimeDIUniverse.SafeType.get[T], instance))

  // sets
  def set[T: RuntimeDIUniverse.Tag]: SetDSL[T] =
    new SetDSL[T](bindings, RuntimeDIUniverse.DIKey.get[Set[T]], Set.empty)
}

object BindingDSL {

  // DSL state machine...

  // .bind{.as, .provider}{.named}

  final class BindDSL[T] private[BindingDSL]
  (
    private val completed: Set[Binding]
    , private val bindKey: RuntimeDIUniverse.DIKey.TypeKey
    , private val bindImpl: ImplDef
  ) extends BindingDSL(
    completed + SingletonBinding(bindKey, bindImpl)
  ) with BindDSLBase
    with BindOnlyNameableDSL {
    override protected type AfterBind = BindOnlyNameableDSL

    override protected type Elem = T

    def named(name: String): BindNamedDSL[T] =
      new BindNamedDSL[T](completed, bindKey.named(name), bindImpl)

    override protected def bind(impl: ImplDef): BindOnlyNameableDSL =
      new BindOnlyNameableDSL.Impl(completed, SingletonBinding(bindKey, impl))
  }

  final class BindNamedDSL[T] private[BindingDSL]
  (
    private val completed: Set[Binding]
    , private val bindKey: RuntimeDIUniverse.DIKey.IdKey[_]
    , private val bindImpl: ImplDef
  ) extends BindingDSL(
    completed + SingletonBinding(bindKey, bindImpl)
  ) with BindDSLBase {
    override protected type AfterBind = BindingDSL

    override protected type Elem = T

    override protected def bind(impl: ImplDef): BindingDSL =
      new BindingDSL(completed + SingletonBinding(bindKey, impl))
  }

  sealed trait BindOnlyNameableDSL extends BindingDSL {
    def named(name: String): BindingDSL
  }

  object BindOnlyNameableDSL {
    private[BindingDSL] final class Impl
    (
      private val completed: Set[Binding]
      , private val binding: SingletonBinding[RuntimeDIUniverse.DIKey.TypeKey]
    ) extends BindingDSL(
      completed + binding
    ) with BindOnlyNameableDSL {
      def named(name: String): BindingDSL =
        new BindingDSL(completed + binding.named(name))
    }
  }

  private[BindingDSL] sealed trait BindDSLBase {
    protected type AfterBind <: BindingDSL

    protected type Elem

    def as[I <: Elem: RuntimeDIUniverse.Tag]: AfterBind =
      bind(ImplDef.TypeImpl(RuntimeDIUniverse.SafeType.get[I]))

    def as[I <: Elem: RuntimeDIUniverse.Tag](instance: I): AfterBind =
      bind(ImplDef.InstanceImpl(RuntimeDIUniverse.SafeType.get[I], instance))

    // "via"?
    def provided[I <: Elem: RuntimeDIUniverse.Tag](f: DIKeyWrappedFunction[I]): AfterBind =
      bind(ImplDef.ProviderImpl(RuntimeDIUniverse.SafeType.get[I], f))

    protected def bind(impl: ImplDef): AfterBind
  }

  // .set{.element, .elementProvider}{.named}

  final class SetDSL[T] private[BindingDSL]
  (
    private val completed: Set[Binding]
    , private val setKey: RuntimeDIUniverse.DIKey.TypeKey
    , private val setElements: Set[ImplDef]
  ) extends BindingDSL(
    completed + EmptySetBinding(setKey) ++ setElements.map(SetBinding(setKey, _))
  ) with SetDSLBase {
    override protected type This = SetDSL[T]

    override protected type Elem = T

    def named(name: String): SetNamedDSL[T] =
      new SetNamedDSL(completed, setKey.named(name), setElements)

    override protected def add(newElement: ImplDef): SetDSL[T] =
      new SetDSL(completed, setKey, setElements + newElement)
  }

  final class SetNamedDSL[T] private[BindingDSL]
  (
    private val completed: Set[Binding]
    , private val setKey: RuntimeDIUniverse.DIKey.IdKey[_]
    , private val setElements: Set[ImplDef]
  ) extends BindingDSL(
    completed + EmptySetBinding(setKey) ++ setElements.map(SetBinding(setKey, _))
  ) with SetDSLBase {
    override protected type This = SetNamedDSL[T]

    override protected type Elem = T

    protected def add(newElement: ImplDef): SetNamedDSL[T] =
      new SetNamedDSL(completed, setKey, setElements + newElement)
  }

  private[BindingDSL] sealed trait SetDSLBase {
    protected type This <: SetDSLBase

    protected type Elem

    def element[I <: Elem: RuntimeDIUniverse.Tag]: This =
      add(ImplDef.TypeImpl(RuntimeDIUniverse.SafeType.get[I]))

    def element[I <: Elem: RuntimeDIUniverse.Tag](instance: I): This =
      add(ImplDef.InstanceImpl(RuntimeDIUniverse.SafeType.get[I], instance))

    def elementProvider(f: DIKeyWrappedFunction[Elem]): This =
      add(ImplDef.ProviderImpl(f.ret, f))

    protected def add(newElement: ImplDef): This
  }



}
