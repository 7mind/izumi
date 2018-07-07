package com.github.pshirshov.izumi.distage.model.definition

import com.github.pshirshov.izumi.distage.model.providers.ProviderMagnet
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse._

object Bindings {
  import Binding._

  def binding[T: Tag]: SingletonBinding[DIKey.TypeKey] =
    SingletonBinding(DIKey.get[T], ImplDef.TypeImpl(SafeType.get[T]))

  def binding[T: Tag, I <: T: Tag]: SingletonBinding[DIKey.TypeKey] =
    SingletonBinding(DIKey.get[T], ImplDef.TypeImpl(SafeType.get[I]))

  def binding[T: Tag, I <: T: Tag](instance: I): SingletonBinding[DIKey.TypeKey] =
    SingletonBinding(DIKey.get[T], ImplDef.InstanceImpl(SafeType.get[I], instance))

  def provider[T: Tag](f: ProviderMagnet[T]): SingletonBinding[DIKey.TypeKey] =
    SingletonBinding(DIKey.get[T], ImplDef.ProviderImpl(f.get.ret, f.get))

  def emptySet[T: Tag]: EmptySetBinding[DIKey.TypeKey] =
    EmptySetBinding(DIKey.get[Set[T]])

  def setElement[T: Tag, I <: T : Tag]: SetElementBinding[DIKey.TypeKey] =
    SetElementBinding(DIKey.get[Set[T]], ImplDef.TypeImpl(SafeType.get[I]))

  def setElement[T: Tag, I <: T: Tag](instance: I): SetElementBinding[DIKey.TypeKey] =
    SetElementBinding(DIKey.get[Set[T]], ImplDef.InstanceImpl(SafeType.get[T], instance))

  def setElementProvider[T: Tag](f: ProviderMagnet[T]): SetElementBinding[DIKey.TypeKey] =
    SetElementBinding(DIKey.get[Set[T]], ImplDef.ProviderImpl(f.get.ret, f.get))

  def setElements[T: Tag, I <: T: Tag](instances: Set[I]): MultiSetElementBinding[DIKey.TypeKey] =
    MultiSetElementBinding(DIKey.get[Set[T]], ImplDef.InstanceImpl(SafeType.get[Set[I]], instances))

  def setElementsProvider[T: Tag](f: ProviderMagnet[Set[T]]): MultiSetElementBinding[DIKey.TypeKey] =
    MultiSetElementBinding(DIKey.get[Set[T]], ImplDef.ProviderImpl(f.get.ret, f.get))
}
