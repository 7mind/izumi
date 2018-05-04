package com.github.pshirshov.izumi.distage.model.definition

import com.github.pshirshov.izumi.distage.model.definition.AbstractModuleBuilder.{BindDSL, SetDSL}
import com.github.pshirshov.izumi.distage.model.definition.Binding.{EmptySetBinding, SetBinding, SingletonBinding}
import com.github.pshirshov.izumi.distage.model.definition.BindingDSL.{BindDSLBase, SetDSLBase}
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
    val uniq = mutableState.add(binding)

    new BindDSL(mutableState, binding, uniq)
  }

  final protected def set[T: Tag]: SetDSL[T] = {
    val binding = Bindings.emptySet[T]
    val uniq = mutableState.add(binding)

    val startingSet: Set[Binding] = if (uniq) Set(binding) else Set.empty

    new SetDSL(mutableState, binding.key, startingSet)
  }

}

trait PluginBuilder extends ModuleBuilder with PluginDef

object AbstractModuleBuilder {

  // DSL state machine...

  // .bind{.as, .provider}{.named}

  private[definition] final class BindDSL[T]
  (
    protected val mutableState: mutable.Set[Binding]
    , protected val binding: SingletonBinding[DIKey.TypeKey]
    , protected val ownBinding: Boolean
  ) extends BindDSLBase[T, BindNamedDSL[T]]
    with BindDSLMutBase {

    def named(name: String): BindNamedDSL[T] = {
      val newBinding = SingletonBinding(binding.key.named(name), binding.implementation)

      replace(newBinding)

      new BindNamedDSL[T](mutableState, newBinding)
    }

    override protected def bind(impl: ImplDef): BindNamedDSL[T] = {
      val newBinding = SingletonBinding(binding.key, impl)

      replace(newBinding)

      new BindNamedDSL[T](mutableState, newBinding)
    }

  }

  private[definition] final class BindNamedDSL[T]
  (
    protected val mutableState: mutable.Set[Binding]
    , protected val binding: Binding.SingletonBinding[_]
  ) extends BindDSLBase[T, Unit]
    with BindDSLMutBase {

    override protected val ownBinding: Boolean = true

    override protected def bind(impl: ImplDef): Unit =
      replace(binding.withImpl(impl))

  }

  private[definition] sealed trait BindDSLMutBase {
    protected def mutableState: mutable.Set[Binding]
    protected val binding: SingletonBinding[_]
    protected val ownBinding: Boolean

    protected def replace(newBinding: Binding): Unit = discard {
      if (ownBinding) {
        mutableState -= binding
      }
      mutableState += newBinding
    }
  }

  // .set{.element, .elementProvider}{.named}

  private[definition] final class SetDSL[T]
  (
    protected val mutableState: mutable.Set[Binding]
    , protected val setKey: DIKey.TypeKey
    , protected val setBindings: Set[Binding]
  ) extends SetDSLBase[T, SetNamedDSL[T]]
    with SetDSLMutBase {

    def named(name: String): SetNamedDSL[T] = {
      val newKey = setKey.named(name)
      val newBindings = replaceKey(newKey)

      new SetNamedDSL(mutableState, newKey, newBindings)
    }

    override protected def add(newElement: ImplDef): SetNamedDSL[T] = {
      val newBinding: Binding = SetBinding(setKey, newElement)

      append(newBinding)
      new SetNamedDSL(mutableState, setKey, setBindings + newBinding)
    }

  }

  private[definition] final class SetNamedDSL[T]
  (
    protected val mutableState: mutable.Set[Binding]
    , protected val setKey: DIKey
    , protected val setBindings: Set[Binding]
  ) extends SetDSLBase[T, SetNamedDSL[T]]
    with SetDSLMutBase {

    protected def add(newElement: ImplDef): SetNamedDSL[T] = {
      val newBinding: Binding = SetBinding(setKey, newElement)

      append(newBinding)
      new SetNamedDSL(mutableState, setKey, setBindings + newBinding)
    }

  }

  private[definition] sealed trait SetDSLMutBase {
    protected def mutableState: mutable.Set[Binding]
    protected def setKey: DIKey
    protected def setBindings: Set[Binding]

    protected def append(binding: Binding): Unit = discard {
      mutableState += binding
    }

    protected def replaceKey(newKey: DIKey): Set[Binding] = {
      val newBindings = (setBindings + EmptySetBinding(setKey)).map(_.withTarget(newKey))

      mutableState --= setBindings
      mutableState ++= newBindings

      newBindings
    }
  }

}
