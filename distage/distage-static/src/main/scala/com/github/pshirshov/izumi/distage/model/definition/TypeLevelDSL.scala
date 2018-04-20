package com.github.pshirshov.izumi.distage.model.definition

import com.github.pshirshov.izumi.distage.model.definition.TypeLevelDSL.Binding.{EmptySetBinding, SetBinding, SingletonBinding}
import com.github.pshirshov.izumi.distage.model.definition.TypeLevelDSL.DIKey.{IdKey, TypeKey}
import com.github.pshirshov.izumi.distage.model.definition.TypeLevelDSL.ImplDef.{InstanceImpl, ProviderImpl, TypeImpl}
import com.github.pshirshov.izumi.distage.model.functions.WrappedFunction.DIKeyWrappedFunction
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse
import shapeless.{::, HList, HNil, Witness}

object TypeLevelDSL {

  sealed trait DIKey

  object DIKey {

    trait TypeKey[T] extends DIKey {
      def repr(implicit ev: RuntimeDIUniverse.Tag[T]): RuntimeDIUniverse.DIKey.TypeKey =
        RuntimeDIUniverse.DIKey.get[T]
    }

    trait IdKey[T, I <: String with Singleton] extends DIKey {
      def repr(implicit ev: RuntimeDIUniverse.Tag[T], w: Witness.Aux[I]): RuntimeDIUniverse.DIKey.IdKey[String with Singleton] =
        RuntimeDIUniverse.DIKey.IdKey(RuntimeDIUniverse.SafeType.get[T], w.value)
    }

  }

  sealed trait ImplDef

  object ImplDef {

    trait TypeImpl[T] extends ImplDef

    trait InstanceImpl[T, I <: T with Singleton] extends ImplDef {
      def repr(implicit ev: RuntimeDIUniverse.Tag[I], w: Witness.Aux[I]) =
        _root_.com.github.pshirshov.izumi.distage.model.definition.ImplDef.InstanceImpl(
          RuntimeDIUniverse.SafeType.get[I]
          , w.value
        )
    }

    object InstanceImpl {
      def apply[T <: AnyRef](impl: T with Singleton): InstanceImpl[T, impl.type] = new InstanceImpl[T, impl.type] {}
    }

    trait ProviderImpl[T, I <: RuntimeDIUniverse.Provider with Singleton] extends ImplDef

  }

  sealed trait Binding

  object Binding {

    trait SingletonBinding[K <: DIKey, I <: ImplDef] extends Binding

    trait SetBinding[K <: DIKey, I <: ImplDef] extends Binding

    trait EmptySetBinding[K <: DIKey] extends Binding

  }

  final class Bindings[BS <: HList](private val dummy: Boolean = true) extends AnyVal {

    def bind[T: RuntimeDIUniverse.Tag]: Bindings[SingletonBinding[TypeKey[T], TypeImpl[T]] :: BS] =
      bind[T, T]

    def bind[T: RuntimeDIUniverse.Tag, I <: T : RuntimeDIUniverse.Tag]: Bindings[SingletonBinding[TypeKey[T], TypeImpl[I]] :: BS] =
      new Bindings[SingletonBinding[TypeKey[T], TypeImpl[I]] :: BS]

    def bind[T: RuntimeDIUniverse.Tag](instance: T with AnyRef with Singleton): Bindings[SingletonBinding[TypeKey[T], InstanceImpl[T, instance.type]] :: BS] =
      new Bindings[SingletonBinding[TypeKey[T], InstanceImpl[T, instance.type]] :: BS]

    def provider[T: RuntimeDIUniverse.Tag](f: DIKeyWrappedFunction[T] with Singleton): Bindings[SingletonBinding[TypeKey[T], ProviderImpl[T, f.type]] :: BS] =
      new Bindings[SingletonBinding[TypeKey[T], ProviderImpl[T, f.type ]] :: BS]

    // sets
    def set[T: RuntimeDIUniverse.Tag]: Bindings[EmptySetBinding[TypeKey[Set[T]]] :: BS] =
      new Bindings[EmptySetBinding[TypeKey[Set[T]]] :: BS]

    def element[T: RuntimeDIUniverse.Tag, I <: T : RuntimeDIUniverse.Tag]: Bindings[SetBinding[TypeKey[Set[T]], TypeImpl[T]] :: BS] =
      new Bindings[SetBinding[TypeKey[Set[T]], TypeImpl[T]] :: BS]

    def element[T: RuntimeDIUniverse.Tag](instance: T with AnyRef with Singleton): Bindings[SetBinding[TypeKey[Set[T]], InstanceImpl[T, instance.type]] :: BS] =
      new Bindings[SetBinding[TypeKey[Set[T]], InstanceImpl[T, instance.type]] :: BS]

    def elementProvider[T: RuntimeDIUniverse.Tag](f: RuntimeDIUniverse.Provider with Singleton): Bindings[SetBinding[TypeKey[Set[T]], ProviderImpl[T, f.type]] :: BS] =
      new Bindings[SetBinding[TypeKey[Set[T]], ProviderImpl[T, f.type]] :: BS]

    // TODO named bindings

    def namedBind[T: RuntimeDIUniverse.Tag](name: String with Singleton) : Bindings[SingletonBinding[IdKey[T, name.type], TypeImpl[T]] :: BS] =
      new Bindings[SingletonBinding[IdKey[T, name.type], TypeImpl[T]] :: BS]
  }

  object Bindings {
    def apply(): Bindings[HNil] = new Bindings[HNil]
  }

}
