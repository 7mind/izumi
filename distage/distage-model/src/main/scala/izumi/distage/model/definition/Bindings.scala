package izumi.distage.model.definition

import izumi.distage.model.providers.ProviderMagnet
import izumi.distage.model.reflection.universe.RuntimeDIUniverse.{DIKey, SafeType, Tag}
import izumi.fundamentals.platform.language.CodePositionMaterializer

object Bindings {
  import Binding._

  def binding[T: Tag](implicit pos: CodePositionMaterializer): SingletonBinding[DIKey.TypeKey] =
    SingletonBinding(DIKey.get[T], ImplDef.TypeImpl(SafeType.get[T]), Set.empty, pos.get.position)

  def binding[T: Tag, I <: T: Tag](implicit pos: CodePositionMaterializer): SingletonBinding[DIKey.TypeKey] =
    SingletonBinding(DIKey.get[T], ImplDef.TypeImpl(SafeType.get[I]), Set.empty, pos.get.position)

  def binding[T: Tag, I <: T: Tag](instance: I)(implicit pos: CodePositionMaterializer): SingletonBinding[DIKey.TypeKey] =
    SingletonBinding(DIKey.get[T], ImplDef.InstanceImpl(SafeType.get[I], instance), Set.empty, pos.get.position)

  def instance[T: Tag](instance: T)(implicit pos: CodePositionMaterializer): SingletonBinding[DIKey.TypeKey] =
    SingletonBinding(DIKey.get[T], ImplDef.InstanceImpl(SafeType.get[T], instance), Set.empty, pos.get.position)

  def reference[T: Tag, I <: T: Tag](implicit pos: CodePositionMaterializer): SingletonBinding[DIKey.TypeKey] =
    SingletonBinding(DIKey.get[T], ImplDef.ReferenceImpl(SafeType.get[I], DIKey.get[I], weak = false))

  def provider[T: Tag](function: ProviderMagnet[T])(implicit pos: CodePositionMaterializer): SingletonBinding[DIKey.TypeKey] =
    SingletonBinding(DIKey.get[T], ImplDef.ProviderImpl(function.get.ret, function.get), Set.empty, pos.get.position)

  def emptySet[T: Tag](implicit pos: CodePositionMaterializer): EmptySetBinding[DIKey.TypeKey] =
    EmptySetBinding(DIKey.get[Set[T]], Set.empty, pos.get.position)

  def setElement[T: Tag, I <: T: Tag](implicit pos: CodePositionMaterializer): SetElementBinding = {
    val setkey = DIKey.get[Set[T]]
    val implKey = DIKey.get[I]
    val implDef = ImplDef.TypeImpl(SafeType.get[I])
    SetElementBinding(DIKey.SetElementKey(setkey, implKey, Some(implDef)), implDef, Set.empty, pos.get.position)
  }

  def setElement[T: Tag, I <: T: Tag](instance: I)(implicit pos: CodePositionMaterializer): SetElementBinding = {
    val setkey = DIKey.get[Set[T]]
    val implKey = DIKey.get[I]
    val implDef = ImplDef.InstanceImpl(SafeType.get[I], instance)
    SetElementBinding(DIKey.SetElementKey(setkey, implKey, Some(implDef)), implDef, Set.empty, pos.get.position)
  }

  def setElementProvider[T: Tag](function: ProviderMagnet[T])(implicit pos: CodePositionMaterializer): SetElementBinding = {
    val setkey = DIKey.get[Set[T]]
    val implKey = DIKey.TypeKey(function.get.ret)
    val implDef = ImplDef.ProviderImpl(function.get.ret, function.get)
    SetElementBinding(DIKey.SetElementKey(setkey, implKey, Some(implDef)), implDef, Set.empty, pos.get.position)
  }

  def todo[K <: DIKey](key: K)(implicit pos: CodePositionMaterializer): SingletonBinding[K] = {
    val provider = ProviderMagnet.todoProvider(key)(pos).get
    SingletonBinding(key, ImplDef.ProviderImpl(provider.ret, provider), Set.empty, pos.get.position)
  }

}
