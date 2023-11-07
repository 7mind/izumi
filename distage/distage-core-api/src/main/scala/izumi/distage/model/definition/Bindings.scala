package izumi.distage.model.definition

import izumi.distage.Subcontext
import izumi.distage.constructors.{ClassConstructor, FactoryConstructor, TraitConstructor}
import izumi.distage.model.definition.Binding.{EmptySetBinding, SetElementBinding, SingletonBinding}
import izumi.distage.model.providers.Functoid
import izumi.distage.model.reflection.DIKey.SetKeyMeta
import izumi.distage.model.reflection.{DIKey, SafeType}
import izumi.fundamentals.platform.language.CodePositionMaterializer
import izumi.reflect.Tag

object Bindings {
  def binding[T: Tag: ClassConstructor](implicit pos: CodePositionMaterializer): SingletonBinding[DIKey.TypeKey] =
    provider[T](ClassConstructor[T])

  def bindingTrait[T: Tag: TraitConstructor](implicit pos: CodePositionMaterializer): SingletonBinding[DIKey.TypeKey] =
    provider[T](TraitConstructor[T])

  def bindingFactory[T: Tag: FactoryConstructor](implicit pos: CodePositionMaterializer): SingletonBinding[DIKey.TypeKey] =
    provider[T](FactoryConstructor[T])

  def binding[T: Tag, I <: T: Tag: ClassConstructor](implicit pos: CodePositionMaterializer): SingletonBinding[DIKey.TypeKey] =
    provider[T](ClassConstructor[I])

  def binding[T: Tag, I <: T: Tag](instance: I)(implicit pos: CodePositionMaterializer): SingletonBinding[DIKey.TypeKey] =
    SingletonBinding(DIKey.get[T], ImplDef.InstanceImpl(SafeType.get[I], instance), Set.empty, pos.get.position)

  def instance[T: Tag](instance: T)(implicit pos: CodePositionMaterializer): SingletonBinding[DIKey.TypeKey] =
    SingletonBinding(DIKey.get[T], ImplDef.InstanceImpl(SafeType.get[T], instance), Set.empty, pos.get.position)

  def reference[T: Tag, I <: T: Tag](implicit pos: CodePositionMaterializer): SingletonBinding[DIKey.TypeKey] =
    SingletonBinding(DIKey.get[T], ImplDef.ReferenceImpl(SafeType.get[I], DIKey.get[I], weak = false), Set.empty, pos.get.position)

  def provider[T: Tag](function: Functoid[T])(implicit pos: CodePositionMaterializer): SingletonBinding[DIKey.TypeKey] =
    SingletonBinding(DIKey.get[T], ImplDef.ProviderImpl(function.get.ret, function.get), Set.empty, pos.get.position)

  def subcontext[T: Tag](
    submodule: ModuleBase,
    functoid: Functoid[T],
    externalKeys: Set[DIKey],
  )(implicit pos: CodePositionMaterializer
  ): SingletonBinding[DIKey.TypeKey] = {
    SingletonBinding(DIKey.get[Subcontext[T]], ImplDef.ContextImpl(functoid.get.ret, functoid.get, submodule, externalKeys), Set.empty, pos.get.position)
  }

  def emptySet[T](implicit tag: Tag[Set[T]], pos: CodePositionMaterializer): EmptySetBinding[DIKey.TypeKey] =
    EmptySetBinding(DIKey.get[Set[T]], Set.empty, pos.get.position)

  def setElement[T: Tag, I <: T: Tag: ClassConstructor](implicit pos: CodePositionMaterializer): SetElementBinding = {
    setElementProvider[T](ClassConstructor[I])
  }

  def setElementTrait[T: Tag, I <: T: Tag: TraitConstructor](implicit pos: CodePositionMaterializer): SetElementBinding = {
    setElementProvider[T](TraitConstructor[I])
  }

  def setElementFactory[T: Tag, I <: T: Tag: FactoryConstructor](implicit pos: CodePositionMaterializer): SetElementBinding = {
    setElementProvider[T](FactoryConstructor[I])
  }

  def setElement[T: Tag, I <: T: Tag](instance: I)(implicit pos: CodePositionMaterializer): SetElementBinding = {
    val setkey = DIKey.get[Set[T]]
    val implKey = DIKey.get[I]
    val implDef = ImplDef.InstanceImpl(SafeType.get[I], instance)
    SetElementBinding(DIKey.SetElementKey(setkey, implKey, SetKeyMeta.WithImpl(implDef)), implDef, Set.empty, pos.get.position)
  }

  def setElementProvider[T: Tag](function: Functoid[T])(implicit pos: CodePositionMaterializer): SetElementBinding = {
    val setkey = DIKey.get[Set[T]]
    val implKey = DIKey.TypeKey(function.get.ret)
    val implDef = ImplDef.ProviderImpl(function.get.ret, function.get)
    SetElementBinding(DIKey.SetElementKey(setkey, implKey, SetKeyMeta.WithImpl(implDef)), implDef, Set.empty, pos.get.position)
  }

  def todo[K <: DIKey](key: K)(implicit pos: CodePositionMaterializer): SingletonBinding[K] = {
    val provider = Functoid.todoProvider(key)(pos).get
    SingletonBinding(key, ImplDef.ProviderImpl(provider.ret, provider), Set.empty, pos.get.position)
  }
}
