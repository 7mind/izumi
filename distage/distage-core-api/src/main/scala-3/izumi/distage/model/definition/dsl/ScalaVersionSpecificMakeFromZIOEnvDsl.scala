package izumi.distage.model.definition.dsl

import izumi.distage.constructors.{ClassConstructor, ZEnvConstructor}
import izumi.distage.model.definition.Lifecycle
import izumi.distage.model.definition.dsl.LifecycleAdapters.ZIOEnvLifecycleTag
import izumi.distage.model.definition.dsl.ModuleDefDSL.{DottyNothing, MakeDSLBase}
import izumi.distage.model.providers.Functoid
import izumi.distage.reflection.macros.{DischargeDummyMacro, IgnorableFunctoidDummyImplicit}
import izumi.functional.bio.data.Morphism1
import izumi.functional.lifecycle.Lifecycle
import izumi.reflect.Tag
import zio.managed.ZManaged
import zio.{Scope, ZEnvironment, ZIO, ZLayer}

implicit class ScalaVersionSpecificMakeFromZIOEnvDsl[T, AfterBind](protected val dsl: MakeDSLBase[T, AfterBind]) extends AnyVal {
  inline def fromZIOEnv[R: ZEnvConstructor, E >: DottyNothing: Tag, I <: T: Tag, N <: IgnorableFunctoidDummyImplicit](
    inline function: N ?=> Functoid[ZIO[Scope & R, E, I]]
  ): AfterBind = {
    val functoid: Functoid[ZIO[Scope & R, E, I]] = DischargeDummyMacro.dischargeDummy[ZIO[Scope & R, E, I], N](function)
    val provider: Functoid[Lifecycle.FromZIO[Any, E, I]] = functoid
      .map2(ZEnvConstructor[R])((zio, r) => zio.provideSomeEnvironment[Scope](_.unionAll[R](r)))
      .map(Lifecycle.fromZIO[Any](_))

    dsl.fromResource(provider)
  }

  def fromZManagedEnv[R: ZEnvConstructor, E >: DottyNothing: Tag, I <: T: Tag](resource: ZManaged[R, E, I]): AfterBind = {
    dsl.fromResourceAdapt(ZEnvConstructor[R].map(resource.provideEnvironment(_)))
  }

  inline def fromZManagedEnv[R: ZEnvConstructor, E >: DottyNothing: Tag, I <: T: Tag, N <: IgnorableFunctoidDummyImplicit](
    inline function: N ?=> Functoid[ZManaged[R, E, I]]
  ): AfterBind = {
    val functoid: Functoid[ZManaged[R, E, I]] = DischargeDummyMacro.dischargeDummy[ZManaged[R, E, I], N](function)
    dsl.fromResourceAdapt(functoid.map2(ZEnvConstructor[R])(_.provideEnvironment(_)))
  }

  inline def fromZLayerEnv[R: ZEnvConstructor, E >: DottyNothing: Tag, I <: T: Tag, N <: IgnorableFunctoidDummyImplicit](
    inline function: N ?=> Functoid[ZLayer[R, E, I]]
  ): AfterBind = {
    val functoid: Functoid[ZLayer[R, E, I]] = DischargeDummyMacro.dischargeDummy[ZLayer[R, E, I], N](function)
    dsl.fromResourceAdapt(functoid.map2(ZEnvConstructor[R])((layer, e) => ZLayer.succeedEnvironment(e) >>> layer))
  }

  def fromZEnvResource[R1 <: Lifecycle[ZIO[Nothing, Any, +_], T]: ClassConstructor](implicit tag: ZIOEnvLifecycleTag[R1, T]): AfterBind = {
    import tag.{A, E, R, ctorR, ev, resourceTag, tagFull}
    val provider = ClassConstructor[R1].map2(ctorR.provider)((r1, zenv) => provideZEnvLifecycle[R, E, A](ev(r1), zenv))(using tagFull)
    dsl.fromResource(provider)(resourceTag)
  }

  def fromZEnvResource[R: ZEnvConstructor, E >: DottyNothing: Tag, I <: T: Tag](resource: Lifecycle[ZIO[R, E, _], I]): AfterBind = {
    val provider = ZEnvConstructor[R].map(provideZEnvLifecycle(resource, _))
    dsl.fromResource[Lifecycle[ZIO[Any, E, _], I], IgnorableFunctoidDummyImplicit](provider)
  }

  inline def fromZEnvResource[R: ZEnvConstructor, E >: DottyNothing: Tag, I <: T: Tag, N <: IgnorableFunctoidDummyImplicit](
    function: N ?=> Functoid[Lifecycle[ZIO[R, E, _], I]]
  ): AfterBind = {
    val functoid: Functoid[Lifecycle[ZIO[R, E, _], I]] = DischargeDummyMacro.dischargeDummy[Lifecycle[ZIO[R, E, _], I], N](function)
    val provider = functoid.map2(ZEnvConstructor[R])(provideZEnvLifecycle)
    dsl.fromResource[Lifecycle[ZIO[Any, E, _], I], IgnorableFunctoidDummyImplicit](provider)
  }

  @inline private def provideZEnvLifecycle[R, E, A](lifecycle: Lifecycle[ZIO[R, E, _], A], zenv: ZEnvironment[R]): Lifecycle[ZIO[Any, E, _], A] = {
    lifecycle.mapK[ZIO[R, E, _], ZIO[Any, E, _]](Morphism1(_.provideEnvironment(zenv)))
  }
}
