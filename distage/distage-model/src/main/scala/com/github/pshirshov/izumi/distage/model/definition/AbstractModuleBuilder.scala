package com.github.pshirshov.izumi.distage.model.definition

import com.github.pshirshov.izumi.distage.model.definition.Binding.{EmptySetBinding, SetElementBinding, SingletonBinding}
import com.github.pshirshov.izumi.distage.model.definition.BindingDSL.{BindDSLBase, SetDSLBase}
import com.github.pshirshov.izumi.distage.model.definition.AbstractModuleBuilder.{BindDSL, SetDSL}
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse._
import com.github.pshirshov.izumi.fundamentals.platform.language.Quirks._

import scala.collection.mutable

trait ModuleBuilder extends ModuleDef {

  protected def initialState: mutable.Set[Binding] = mutable.HashSet.empty[Binding]

  protected def freeze(state: mutable.Set[Binding]): Set[Binding] = state.toSet

  final private[this] val mutableState: mutable.Set[Binding] = initialState

  final override def bindings: Set[Binding] = freeze(mutableState)

  final protected def bind[T: Tag]: BindDSL[T] = {
    val binding = Bindings.binding[T]
    mutableState += binding
    new BindDSL(mutableState, binding)
  }

  final protected def bind[T: Tag](instance: T): BindDSL[T] = {
    val binding = Bindings.binding(instance)
    mutableState += binding
    new BindDSL(mutableState, binding)
  }

  final protected def set[T: Tag]: SetDSL[T] = {
    val binding = Bindings.emptySet[T]
    mutableState += binding
    new SetDSL(mutableState, binding.key, Set())
  }

}


object AbstractModuleBuilder {

  // DSL state machine...

  // .bind{.as, .provider}{.named}

  private[definition] final class BindDSL[T]
  (
    protected val mutableState: mutable.Set[Binding]
    , protected val binding: SingletonBinding[DIKey.TypeKey]
  ) extends BindDSLBase[T, BindOnlyNameableDSL]
    with BindDSLMutBase {
    def named(name: String): BindNamedDSL[T] = {
      val newBinding = SingletonBinding(binding.key.named(name), binding.implementation)

      replace(newBinding)

      new BindNamedDSL[T](mutableState, newBinding)
    }

    override protected def bind(impl: ImplDef): BindOnlyNameableDSL = {
      val newBinding = SingletonBinding(binding.key, impl)

      replace(newBinding)

      new BindOnlyNameableDSL.Impl(mutableState, newBinding)
    }
  }

  private[definition] final class BindNamedDSL[T]
  (
    protected val mutableState: mutable.Set[Binding]
    , protected val binding: SingletonBinding[DIKey.IdKey[_]]
  ) extends BindDSLBase[T, Unit]
    with BindDSLMutBase {
    override protected def bind(impl: ImplDef): Unit =
      replace(binding.withImpl(impl))
  }

  private[definition] sealed trait BindOnlyNameableDSL {
    def named(name: String): Unit
  }

  private[definition] object BindOnlyNameableDSL {
    private[AbstractModuleBuilder] final class Impl
    (
      protected val mutableState: mutable.Set[Binding]
      , protected val binding: SingletonBinding[DIKey.TypeKey]
    ) extends BindOnlyNameableDSL
      with BindDSLMutBase {
      def named(name: String): Unit =
        replace(binding.named(name))
    }
  }

  private[definition] sealed trait BindDSLMutBase {
    protected def mutableState: mutable.Set[Binding]
    protected val binding: SingletonBinding[_]

    protected def replace(newBinding: Binding): Unit = discard {
      mutableState -= binding
      mutableState += newBinding
    }
  }

  // .set{.element, .elementProvider}{.named}

  private[definition] final class SetDSL[T]
  (
    protected val mutableState: mutable.Set[Binding]
    , protected val setKey: DIKey.TypeKey
    , protected val setElements: Set[ImplDef]
  ) extends SetDSLBase[T, SetDSL[T]]
    with SetDSLMutBase {
    def named(name: String): SetNamedDSL[T] = {
      val newKey = setKey.named(name)
      replaceKey(newKey)
      new SetNamedDSL(mutableState, newKey, setElements)
    }

    override protected def add(newElement: ImplDef): SetDSL[T] = {
      append(newElement)
      new SetDSL(mutableState, setKey, setElements + newElement)
    }
  }

  private[definition] final class SetNamedDSL[T]
  (
    protected val mutableState: mutable.Set[Binding]
    , protected val setKey: DIKey.IdKey[_]
    , protected val setElements: Set[ImplDef]
  ) extends SetDSLBase[T, SetNamedDSL[T]]
    with SetDSLMutBase {
    protected def add(newElement: ImplDef): SetNamedDSL[T] = {
      append(newElement)
      new SetNamedDSL(mutableState, setKey, setElements + newElement)
    }
  }

  private[definition] sealed trait SetDSLMutBase {
    protected def mutableState: mutable.Set[Binding]
    protected def setKey: DIKey
    protected def setElements: Set[ImplDef]

    protected def append(impl: ImplDef): Unit = discard {
      mutableState += SetElementBinding(setKey, impl)
    }

    protected def replaceKey(key: DIKey): Unit = discard {
      mutableState.retain(_.key != key)
      val newElements = setElements.map(SetElementBinding(key, _): Binding) + EmptySetBinding(key)
      mutableState ++= newElements
    }
  }

}
