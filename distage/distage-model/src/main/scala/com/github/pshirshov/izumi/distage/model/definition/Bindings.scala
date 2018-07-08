package com.github.pshirshov.izumi.distage.model.definition

import com.github.pshirshov.izumi.distage.model.exceptions.TODOBindingException
import com.github.pshirshov.izumi.distage.model.providers.ProviderMagnet
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse._
import com.github.pshirshov.izumi.fundamentals.reflection.MacroUtil.EnclosingPosition

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

  def todo[K <: DIKey](key: K)(implicit pos: EnclosingPosition): SingletonBinding[K] = {
    val provider = todoProvider(key)(pos).get
    SingletonBinding(key, ImplDef.ProviderImpl(provider.ret, provider))
  }

  def todoProvider(key: DIKey)(implicit pos: EnclosingPosition): ProviderMagnet[_] =
    new ProviderMagnet[Any](
      Provider.ProviderImpl(
         Seq.empty
         , key.tpe
         , _ => throw new TODOBindingException(
           s"Tried to instantiate a 'TODO' binding for $key defined at ${pos.file}:${pos.line}!\nDefined in module ${pos.applicationPointId}", key, pos)
       )
    )

}
