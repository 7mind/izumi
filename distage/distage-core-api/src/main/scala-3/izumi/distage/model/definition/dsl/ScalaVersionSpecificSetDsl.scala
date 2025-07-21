package izumi.distage.model.definition.dsl

import izumi.distage.model.definition.dsl.AnyKindShim.LifecycleF
import izumi.distage.model.definition.{ImplDef, Lifecycle}
import izumi.distage.model.definition.dsl.LifecycleAdapters.LifecycleTag
import izumi.distage.model.definition.dsl.ModuleDefDSL.SetDSLBase
import izumi.distage.model.providers.Functoid
import izumi.distage.model.reflection.SafeType
import izumi.distage.reflection.macros.{DischargeDummyMacro, IgnorableFunctoidDummyImplicit}
import izumi.fundamentals.platform.language.CodePositionMaterializer
import izumi.reflect.{Tag, TagK}

trait ScalaVersionSpecificSetDsl[T, AfterAdd, AfterMultiAdd] { self: SetDSLBase[T, AfterAdd, AfterMultiAdd] =>
  inline final def add[I <: T: Tag, N <: IgnorableFunctoidDummyImplicit](inline function: N ?=> Functoid[I])(implicit pos: CodePositionMaterializer): AfterAdd = {
    val functoid: Functoid[I] = DischargeDummyMacro.dischargeDummy[I, N](function)
    appendElement(ImplDef.ProviderImpl(functoid.get.ret, functoid.get), pos)
  }

  inline final def addEffect[F[_]: TagK, I <: T: Tag, N <: IgnorableFunctoidDummyImplicit](
    inline function: N ?=> Functoid[F[I]]
  )(implicit pos: CodePositionMaterializer
  ): AfterAdd = {
    val functoid: Functoid[F[I]] = DischargeDummyMacro.dischargeDummy[F[I], N](function)
    appendElement(ImplDef.EffectImpl(SafeType.get[I], SafeType.getK[F], ImplDef.ProviderImpl(functoid.get.ret, functoid.get)), pos)
  }

  inline final def addResource[R, N <: IgnorableFunctoidDummyImplicit](
    inline function: N ?=> Functoid[R & Lifecycle[LifecycleF, T]]
  )(implicit tag: LifecycleTag[R],
    pos: CodePositionMaterializer,
  ): AfterAdd = {
    import tag.*
    val functoid: Functoid[R & Lifecycle[LifecycleF, T]] = DischargeDummyMacro.dischargeDummy[R & Lifecycle[LifecycleF, T], N](function)
    appendElement(ImplDef.ResourceImpl(SafeType.get[A], SafeType.getK[F], ImplDef.ProviderImpl(SafeType.get[R], functoid.get)), pos)
  }

  inline final def addSet[I <: Set[? <: T], N <: IgnorableFunctoidDummyImplicit](
    inline function: N ?=> Functoid[I]
  )(implicit pos: CodePositionMaterializer
  ): AfterMultiAdd = {
    val functoid: Functoid[I] = DischargeDummyMacro.dischargeDummy[I, N](function)
    multiSetAdd(ImplDef.ProviderImpl(functoid.get.ret, functoid.get), pos)
  }
}
