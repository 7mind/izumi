package izumi.distage.model.definition

import izumi.distage.model.providers.ProviderMagnet
import izumi.distage.model.reflection.universe.RuntimeDIUniverse.{DIKey, SafeType, Tag}
import izumi.fundamentals.reflection.CodePositionMaterializer

object Bindings {
  import Binding._

  def binding[T: Tag](implicit pos: CodePositionMaterializer): SingletonBinding[DIKey.TypeKey] =
    SingletonBinding(DIKey.get[T], ImplDef.TypeImpl(SafeType.get[T]))

  def binding[T: Tag, I <: T: Tag](implicit pos: CodePositionMaterializer): SingletonBinding[DIKey.TypeKey] =
    SingletonBinding(DIKey.get[T], ImplDef.TypeImpl(SafeType.get[I]))

  def binding[T: Tag, I <: T: Tag](instance: I)(implicit pos: CodePositionMaterializer): SingletonBinding[DIKey.TypeKey] =
    SingletonBinding(DIKey.get[T], ImplDef.InstanceImpl(SafeType.get[I], instance))

  def instance[T: Tag](instance: T)(implicit pos: CodePositionMaterializer): SingletonBinding[DIKey.TypeKey] =
    SingletonBinding(DIKey.get[T], ImplDef.InstanceImpl(SafeType.get[T], instance))

  def provider[T: Tag](function: ProviderMagnet[T])(implicit pos: CodePositionMaterializer): SingletonBinding[DIKey.TypeKey] =
    SingletonBinding(DIKey.get[T], ImplDef.ProviderImpl(function.get.ret, function.get))

  def emptySet[T: Tag](implicit pos: CodePositionMaterializer): EmptySetBinding[DIKey.TypeKey] =
    EmptySetBinding(DIKey.get[Set[T]], Set.empty, pos.get.position)

  def setElement[T: Tag, I <: T: Tag](implicit pos: CodePositionMaterializer): SetElementBinding[DIKey.TypeKey] =
    SetElementBinding(DIKey.get[Set[T]], ImplDef.TypeImpl(SafeType.get[I]))

  def setElement[T: Tag, I <: T: Tag](instance: I)(implicit pos: CodePositionMaterializer): SetElementBinding[DIKey.TypeKey] =
    SetElementBinding(DIKey.get[Set[T]], ImplDef.InstanceImpl(SafeType.get[T], instance))

  def setElementProvider[T: Tag](function: ProviderMagnet[T])(implicit pos: CodePositionMaterializer): SetElementBinding[DIKey.TypeKey] =
    SetElementBinding(DIKey.get[Set[T]], ImplDef.ProviderImpl(function.get.ret, function.get))

  def todo[K <: DIKey](key: K)(implicit pos: CodePositionMaterializer): SingletonBinding[K] = {
    val provider = ProviderMagnet.todoProvider(key)(pos).get
    SingletonBinding(key, ImplDef.ProviderImpl(provider.ret, provider))
  }

}
