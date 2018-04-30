package com.github.pshirshov.izumi.distage.model.definition

import com.github.pshirshov.izumi.distage.model.definition.Binding.SingletonBinding
import com.github.pshirshov.izumi.distage.model.definition.BindingDSL.{BindDSLBase, SetDSLBase}
import com.github.pshirshov.izumi.distage.model.functions.WrappedFunction.DIKeyWrappedFunction
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse
import com.github.pshirshov.izumi.fundamentals.platform.language.Quirks._

import scala.collection.mutable

trait ModuleBuilder {

  protected def initialState: mutable.Set[Binding] = mutable.HashSet.empty[Binding]

  protected def freeze(state: mutable.Set[Binding]): Set[Binding] = state.toSet

  final private[this] val mutableState: mutable.Set[Binding] = initialState

  def build: AbstractModuleDef = freeze(mutableState)

  final protected def bind[T: RuntimeDIUniverse.Tag]: Unit = {
    bind[T, T]
  }

  final protected def bind[T: RuntimeDIUniverse.Tag, I <: T: RuntimeDIUniverse.Tag]: Unit = { val _ =
    mutableState += Bindings.binding[T, I]
  }

  final protected def bind[T: RuntimeDIUniverse.Tag](instance: T): Unit = { val _ =
    mutableState += Bindings.binding(instance)
  }

  final protected def provider[T: RuntimeDIUniverse.Tag](f: DIKeyWrappedFunction[T]): Unit = { val _ =
    mutableState += Bindings.provider(f)
  }

}

object ModuleBuilder {

  // DSL state machine...

  // .bind{.as, .provider}{.named}

  private[definition] final class BindDSL[T] private[ModuleBuilder]
  (
    private val mutableState: mutable.Set[Binding]
    , private val bindKey: RuntimeDIUniverse.DIKey.TypeKey
    , private val bindImpl: ImplDef
  ) extends BindDSLBase {
    override protected type AfterBind = BindOnlyNameableDSL

    override protected type Elem = T

    def named(name: String): BindNamedDSL[T] = {
      val newKey = bindKey.named(name)

      mutableState -= SingletonBinding(bindKey, bindImpl)
      mutableState += SingletonBinding(newKey, bindImpl)

      new BindNamedDSL[T](mutableState, newKey, bindImpl)
    }

    override protected def bind(impl: ImplDef): BindOnlyNameableDSL = {
      val newBinding = SingletonBinding(bindKey, impl)

      mutableState -= SingletonBinding(bindKey, bindImpl)
      mutableState += newBinding

      new BindOnlyNameableDSL.Impl(mutableState, newBinding)
    }
  }

  private[definition] final class BindNamedDSL[T] private[ModuleBuilder]
  (
    private val mutableState: mutable.Set[Binding]
    , private val bindKey: RuntimeDIUniverse.DIKey.IdKey[_]
    , private val bindImpl: ImplDef
  ) extends BindDSLBase {
    override protected type AfterBind = Unit

    override protected type Elem = T

    override protected def bind(impl: ImplDef): Unit = discard {
      mutableState -= SingletonBinding(bindKey, bindImpl)
      mutableState += SingletonBinding(bindImpl, impl)
    }

  }

  private[definition] sealed trait BindOnlyNameableDSL {
    def named(name: String): Unit
  }

  private[definition] object BindOnlyNameableDSL {
    private[ModuleBuilder] final class Impl
    (
      private val mutableState: mutable.Set[Binding]
      , private val binding: SingletonBinding[RuntimeDIUniverse.DIKey.TypeKey]
    ) extends BindOnlyNameableDSL {
      def named(name: String): Unit = discard {
        mutableState -= binding
        mutableState += binding.named(name)
      }
    }
  }

  // .set{.element, .elementProvider}{.named}

  private[definition] final class SetDSL[T] private[ModuleBuilder]
  (
    private val mutableState: mutable.Set[Binding]
    , private val setKey: RuntimeDIUniverse.DIKey.TypeKey
    , private val setElements: Set[ImplDef]
  ) extends SetDSLBase
    with SetDSLMutBase {
    override protected type This = SetDSL[T]

    override protected type Elem = T

    def named(name: String): SetNamedDSL[T] = {
      new SetNamedDSL(completed, setKey.named(name), setElements)
    }

    override protected def add(newElement: ImplDef): SetDSL[T] =
      new SetDSL(completed, setKey, setElements + newElement)
  }

  private[definition] final class SetNamedDSL[T] private[ModuleBuilder]
  (
    private val completed: Set[Binding]
    , private val setKey: RuntimeDIUniverse.DIKey.IdKey[_]
    , private val setElements: Set[ImplDef]
  ) extends SetDSLBase {
    override protected type This = SetNamedDSL[T]

    override protected type Elem = T

    protected def add(newElement: ImplDef): SetNamedDSL[T] =
      new SetNamedDSL(completed, setKey, setElements + newElement)
  }

  private[ModuleBuilder] sealed trait SetDSLMutBase {

  }

}
