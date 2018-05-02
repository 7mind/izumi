package com.github.pshirshov.izumi.distage.model.definition

import com.github.pshirshov.izumi.distage.model.definition.Binding.{EmptySetBinding, SetBinding, SingletonBinding}
import com.github.pshirshov.izumi.distage.model.functions.WrappedFunction.DIKeyWrappedFunction
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse._

class BindingDSL private[definition] (val bindings: Set[Binding]) extends ModuleDef {
  import BindingDSL._

  def bind[T: Tag]: BindDSL[T] =
    new BindDSL(bindings, DIKey.get[T], ImplDef.TypeImpl(SafeType.get[T]))

  def bind[T: Tag](instance: T): BindDSL[T] =
    new BindDSL(bindings, DIKey.get[T], ImplDef.InstanceImpl(SafeType.get[T], instance))

  // sets
  def set[T: Tag]: SetDSL[T] =
    new SetDSL[T](bindings, DIKey.get[Set[T]], Set.empty)
}

object BindingDSL {

  // DSL state machine...

  // .bind{.as, .provider}{.named}

  private[definition] final class BindDSL[T]
  (
    private val completed: Set[Binding]
    , private val bindKey: DIKey.TypeKey
    , private val bindImpl: ImplDef
  ) extends BindingDSL(
    completed + SingletonBinding(bindKey, bindImpl)
  ) with BindDSLBase[T, BindOnlyNameableDSL]
    with BindOnlyNameableDSL {
    def named(name: String): BindNamedDSL[T] =
      new BindNamedDSL[T](completed, bindKey.named(name), bindImpl)

    override protected def bind(impl: ImplDef): BindOnlyNameableDSL =
      new BindOnlyNameableDSL.Impl(completed, SingletonBinding(bindKey, impl))
  }

  private[definition] final class BindNamedDSL[T]
  (
    private val completed: Set[Binding]
    , private val bindKey: DIKey.IdKey[_]
    , private val bindImpl: ImplDef
  ) extends BindingDSL(
    completed + SingletonBinding(bindKey, bindImpl)
  ) with BindDSLBase[T, BindingDSL] {
    override protected def bind(impl: ImplDef): BindingDSL =
      new BindingDSL(completed + SingletonBinding(bindKey, impl))
  }

  private[definition] sealed trait BindOnlyNameableDSL extends BindingDSL {
    def named(name: String): BindingDSL
  }

  private[definition] object BindOnlyNameableDSL {
    final class Impl
    (
      private val completed: Set[Binding]
      , private val binding: SingletonBinding[DIKey.TypeKey]
    ) extends BindingDSL(
      completed + binding
    ) with BindOnlyNameableDSL {
      def named(name: String): BindingDSL =
        new BindingDSL(completed + binding.named(name))
    }
  }

  private[definition] trait BindDSLBase[T, AfterBind] {
    def as[I <: T: Tag]: AfterBind =
      bind(ImplDef.TypeImpl(SafeType.get[I]))

    def as[I <: T: Tag](instance: I): AfterBind =
      bind(ImplDef.InstanceImpl(SafeType.get[I], instance))

    // "via"?
    def provided[I <: T: Tag](f: DIKeyWrappedFunction[I]): AfterBind =
      bind(ImplDef.ProviderImpl(SafeType.get[I], f))

    protected def bind(impl: ImplDef): AfterBind
  }

  // .set{.element, .elementProvider}{.named}

  private[definition] class SetDSL[T]
  (
    private val completed: Set[Binding]
    , private val setKey: DIKey.TypeKey
    , private val setElements: Set[ImplDef]
  ) extends BindingDSL(
    completed + EmptySetBinding(setKey) ++ setElements.map(SetBinding(setKey, _))
  ) with SetDSLBase[T, SetDSL[T]] {
    def named(name: String): SetNamedDSL[T] =
      new SetNamedDSL(completed, setKey.named(name), setElements)

    override protected def add(newElement: ImplDef): SetDSL[T] =
      new SetDSL(completed, setKey, setElements + newElement)
  }

  private[definition] final class SetNamedDSL[T]
  (
    private val completed: Set[Binding]
    , private val setKey: DIKey.IdKey[_]
    , private val setElements: Set[ImplDef]
  ) extends BindingDSL(
    completed + EmptySetBinding(setKey) ++ setElements.map(SetBinding(setKey, _))
  ) with SetDSLBase[T, SetNamedDSL[T]] {
    protected def add(newElement: ImplDef): SetNamedDSL[T] =
      new SetNamedDSL(completed, setKey, setElements + newElement)
  }

  private[definition] trait SetDSLBase[T, AfterAdd] {
    def element[I <: T: Tag]: AfterAdd =
      add(ImplDef.TypeImpl(SafeType.get[I]))

    def element[I <: T: Tag](instance: I): AfterAdd =
      add(ImplDef.InstanceImpl(SafeType.get[I], instance))

    def elementProvider(f: DIKeyWrappedFunction[T]): AfterAdd =
      add(ImplDef.ProviderImpl(f.ret, f))

    protected def add(newElement: ImplDef): AfterAdd
  }

}
