package com.github.pshirshov.izumi.distage.model.definition

import com.github.pshirshov.izumi.distage.model.definition.TypeLevelDSL.Binding._
import com.github.pshirshov.izumi.distage.model.definition.TypeLevelDSL.DIKey._
import com.github.pshirshov.izumi.distage.model.definition.TypeLevelDSL.ImplDef._
import com.github.pshirshov.izumi.distage.model.providers.ProviderMagnet
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse
import com.github.pshirshov.izumi.distage.model.{definition => valuedef}
import shapeless.{::, HList, HNil, Witness}

/**
* Sketch of a DSL that exposes rich type information that can then be picked up and used by a macro to do planning & checks
* at compile time. Note that the DSL is, by necessity, immutable, which is a mismatch with standard ModuleDef.
*
* A macro analysing syntax trees may instead be employed, to not burden the user with a different version of the syntax.
* Esp. that ModuleDef syntax is quite simple (but macro will lose user extensions)
*/
object TypeLevelDSL {

  sealed trait DIKey

  object DIKey {

    trait TypeKey[T] extends DIKey {
      def repr(implicit ev: RuntimeDIUniverse.Tag[T]): RuntimeDIUniverse.DIKey.TypeKey =
        RuntimeDIUniverse.DIKey.get[T]
    }

    trait IdKey[T, I <: String with Singleton] extends DIKey {
      def repr(implicit ev: RuntimeDIUniverse.Tag[T], w: Witness.Aux[I]): RuntimeDIUniverse.DIKey.IdKey[w.T] =
        RuntimeDIUniverse.DIKey.IdKey(RuntimeDIUniverse.SafeType.get[T], w.value)
    }

  }

  sealed trait ImplDef

  object ImplDef {

    trait TypeImpl[T] extends ImplDef {
      def repr(implicit ev: RuntimeDIUniverse.Tag[T]): valuedef.ImplDef.TypeImpl =
        valuedef.ImplDef.TypeImpl(RuntimeDIUniverse.SafeType.get[T])
    }

    trait InstanceImpl[T, I <: T with Singleton] extends ImplDef {
      def repr(implicit ev: RuntimeDIUniverse.Tag[I], w: Witness.Aux[I]): valuedef.ImplDef.InstanceImpl =
        valuedef.ImplDef.InstanceImpl(RuntimeDIUniverse.SafeType.get[I], w.value)
    }
    object InstanceImpl {
      def apply[T <: AnyRef](impl: T with Singleton): InstanceImpl[T, impl.type] = new InstanceImpl[T, impl.type] {}
    }

    trait ProviderImpl[T, I <: ProviderMagnet[T] with Singleton] extends ImplDef {
      def repr(implicit ev: RuntimeDIUniverse.Tag[I], w: Witness.Aux[I]): valuedef.ImplDef.ProviderImpl =
        valuedef.ImplDef.ProviderImpl(RuntimeDIUniverse.SafeType.get[I], w.value.get)
    }

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

    def provider[T: RuntimeDIUniverse.Tag](f: ProviderMagnet[T] with Singleton): Bindings[SingletonBinding[TypeKey[T], ProviderImpl[T, f.type]] :: BS] =
      new Bindings[SingletonBinding[TypeKey[T], ProviderImpl[T, f.type ]] :: BS]

    // sets
    def set[T: RuntimeDIUniverse.Tag]: Bindings[EmptySetBinding[TypeKey[Set[T]]] :: BS] =
      new Bindings[EmptySetBinding[TypeKey[Set[T]]] :: BS]

    def element[T: RuntimeDIUniverse.Tag, I <: T : RuntimeDIUniverse.Tag]: Bindings[SetBinding[TypeKey[Set[T]], TypeImpl[T]] :: BS] =
      new Bindings[SetBinding[TypeKey[Set[T]], TypeImpl[T]] :: BS]

    def element[T: RuntimeDIUniverse.Tag](instance: T with AnyRef with Singleton): Bindings[SetBinding[TypeKey[Set[T]], InstanceImpl[T, instance.type]] :: BS] =
      new Bindings[SetBinding[TypeKey[Set[T]], InstanceImpl[T, instance.type]] :: BS]

    def elementProvider[T: RuntimeDIUniverse.Tag](f: ProviderMagnet[T] with Singleton): Bindings[SetBinding[TypeKey[Set[T]], ProviderImpl[T, f.type]] :: BS] =
      new Bindings[SetBinding[TypeKey[Set[T]], ProviderImpl[T, f.type]] :: BS]

    // TODO named bindings

    def namedBind[T: RuntimeDIUniverse.Tag](name: String with Singleton): Bindings[SingletonBinding[IdKey[T, name.type], TypeImpl[T]] :: BS] =
      new Bindings[SingletonBinding[IdKey[T, name.type], TypeImpl[T]] :: BS]
  }

  object Bindings {
    def apply(): Bindings[HNil] = new Bindings[HNil]
  }

}
