package com.github.pshirshov.izumi.distage.model.definition

import com.github.pshirshov.izumi.distage.model.functions.WrappedFunction.DIKeyWrappedFunction
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse._

object Bindings {
  import Binding._

  def binding[T: Tag]: SingletonBinding[DIKey.TypeKey] =
    SingletonBinding(DIKey.get[T], ImplDef.TypeImpl(SafeType.get[T]))

  def binding[T: Tag, I <: T: Tag]: SingletonBinding[DIKey.TypeKey] =
    SingletonBinding(DIKey.get[T], ImplDef.TypeImpl(SafeType.get[I]))

  def binding[T: Tag, I <: T: Tag](instance: I): SingletonBinding[DIKey.TypeKey] =
    SingletonBinding(DIKey.get[T], ImplDef.InstanceImpl(SafeType.get[I], instance))

  def provider[T: Tag](f: DIKeyWrappedFunction[T]): SingletonBinding[DIKey.TypeKey] =
    SingletonBinding(DIKey.get[T], ImplDef.ProviderImpl(f.ret, f))

  def emptySet[T: Tag]: EmptySetBinding[DIKey.TypeKey] =
    EmptySetBinding(DIKey.get[Set[T]])

  def setElement[T: Tag, I <: T : Tag]: SetBinding[DIKey.TypeKey] =
    SetBinding(DIKey.get[Set[T]], ImplDef.TypeImpl(SafeType.get[I]))

  def setElement[T: Tag, I <: T: Tag](instance: I): SetBinding[DIKey.TypeKey] =
    SetBinding(DIKey.get[Set[T]], ImplDef.InstanceImpl(SafeType.get[T], instance))

  def setElementProvider[T: Tag](f: DIKeyWrappedFunction[T]): SetBinding[DIKey.TypeKey] =
    SetBinding(DIKey.get[Set[T]], ImplDef.ProviderImpl(f.ret, f))
}
