package com.github.pshirshov.izumi.distage.model.definition

import com.github.pshirshov.izumi.distage.model.definition.Binding.SingletonBinding
import com.github.pshirshov.izumi.distage.model.functions.WrappedFunction.DIKeyWrappedFunction
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse

import scala.collection.mutable

trait ModuleBuilder {

  protected def initialState: mutable.Set[Binding] = mutable.HashSet.empty[Binding]

  protected def freeze(state: mutable.Set[Binding]): Set[Binding] = state.toSet

  final private[this] val mutableState: mutable.Set[Binding] = initialState

  def build: ModuleDef = freeze(mutableState)

  final protected def bind[T: RuntimeDIUniverse.Tag]: Unit = {
    bind[T, T]
  }

  final protected def bind[T: RuntimeDIUniverse.Tag, I <: T: RuntimeDIUniverse.Tag]: Unit = { val _ =
    mutableState += Binding.SingletonBinding(RuntimeDIUniverse.DIKey.get[T], ImplDef.TypeImpl(RuntimeDIUniverse.SafeType.get[I]))
  }

  final protected def bind[T: RuntimeDIUniverse.Tag](instance: T): Unit = { val _ =
    mutableState += Binding.SingletonBinding(RuntimeDIUniverse.DIKey.get[T], ImplDef.InstanceImpl(RuntimeDIUniverse.SafeType.get[T], instance))
  }

  final protected def provider[T: RuntimeDIUniverse.Tag](f: DIKeyWrappedFunction[T]): Unit = { val _ =
    mutableState += SingletonBinding(RuntimeDIUniverse.DIKey.get[T], ImplDef.ProviderImpl(f.ret, f))
  }

}
