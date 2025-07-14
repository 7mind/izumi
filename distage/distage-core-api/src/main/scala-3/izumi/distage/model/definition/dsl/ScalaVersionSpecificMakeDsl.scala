package izumi.distage.model.definition.dsl

import izumi.distage.model.definition.dsl.AnyKindShim.LifecycleF
import izumi.distage.model.definition.dsl.LifecycleAdapters.LifecycleTag
import izumi.distage.model.definition.dsl.ModuleDefDSL.MakeDSLBase
import izumi.distage.model.definition.{ImplDef, Lifecycle}
import izumi.distage.model.providers.Functoid
import izumi.distage.model.reflection.SafeType
import izumi.distage.reflection.macros.{DischargeDummyMacro, IgnorableFunctoidDummyImplicit, UnignorableDummyImplicit}
import izumi.reflect.{Tag, TagK}

trait ScalaVersionSpecificMakeDsl[T, AfterBind] { self: MakeDSLBase[T, AfterBind] =>
  inline def from[I <: T, N <: IgnorableFunctoidDummyImplicit](inline f: N ?=> Functoid[I]): AfterBind = {
    val functoid: Functoid[I] = DischargeDummyMacro.dischargeDummy[I, N](f)
    bind(ImplDef.ProviderImpl(functoid.get.ret, functoid.get))
  }

  inline def fromEffect[F[_]: TagK, I <: T: Tag, N <: IgnorableFunctoidDummyImplicit](inline f: N ?=> Functoid[F[I]]): AfterBind = {
    val functoid: Functoid[F[I]] = DischargeDummyMacro.dischargeDummy[F[I], N](f)
    bind(ImplDef.EffectImpl(SafeType.get[I], SafeType.getK[F], ImplDef.ProviderImpl(functoid.get.ret, functoid.get)))
  }

  inline def fromEffectDebug[F[_]: TagK, I <: T: Tag, N <: IgnorableFunctoidDummyImplicit](inline f: N ?=> Functoid[F[I]]): AfterBind = {
    compiletime.error(
      "input: " + compiletime.codeOf((() => f): () => N ?=> Functoid[F[I]]) +
      "\nresult: " + compiletime.codeOf(DischargeDummyMacro.dischargeDummy[F[I], N](f))
    )
    fromEffect[F, I, N](f)
  }

  inline def fromEffectDebugInput[F[_]: TagK, I <: T: Tag, N <: IgnorableFunctoidDummyImplicit](inline f: N ?=> Functoid[F[I]]): AfterBind = {
    compiletime.error("input: " + compiletime.codeOf((() => f): () => N ?=> Functoid[F[I]]))
    fromEffect[F, I, N](f)
  }

  inline def fromResource[R, N <: IgnorableFunctoidDummyImplicit](inline f: N ?=> Functoid[R & Lifecycle[LifecycleF, T]])(implicit tag: LifecycleTag[R]): AfterBind = {
    import tag.*
    val functoid: Functoid[R & Lifecycle[LifecycleF, T]] = DischargeDummyMacro.dischargeDummy[R & Lifecycle[LifecycleF, T], N](f)
    bind(ImplDef.ResourceImpl(SafeType.get[A], SafeType.getK[F], ImplDef.ProviderImpl(SafeType.get[R], functoid.get)))
  }

  inline def noCapture[I <: T: Tag](inline f: Functoid[I]): AfterBind = {
    from(_ ?=> f)
  }

  inline def fromEffectNoCapture[F[_]: TagK, I <: T: Tag](inline f: Functoid[F[I]]): AfterBind = {
    fromEffect(_ ?=> f)
  }
}

object ScalaVersionSpecificMakeDsl {
  inline def withAllImplicits[I: Tag, N <: UnignorableDummyImplicit](f: N ?=> I): I = f(using null.asInstanceOf[N])
}
