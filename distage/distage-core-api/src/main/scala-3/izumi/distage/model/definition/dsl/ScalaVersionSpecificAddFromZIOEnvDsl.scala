package izumi.distage.model.definition.dsl

import izumi.distage.constructors.{ClassConstructor, ZEnvConstructor}
import izumi.distage.model.definition.Lifecycle
import izumi.distage.model.definition.dsl.LifecycleAdapters.ZIOEnvLifecycleTag
import izumi.distage.model.definition.dsl.ModuleDefDSL.{DottyNothing, SetDSLBase}
import izumi.distage.model.providers.Functoid
import izumi.distage.reflection.macros.{DischargeDummyMacro, IgnorableFunctoidDummyImplicit}
import izumi.functional.bio.data.Morphism1
import izumi.fundamentals.platform.language.CodePositionMaterializer
import izumi.reflect.Tag
import zio.managed.ZManaged
import zio.{IO, ZEnvironment, ZIO, ZLayer}

implicit final class ScalaVersionSpecificAddFromZIOEnvDsl[T, AfterAdd, AfterMultiAdd](protected val dsl: SetDSLBase[T, AfterAdd, AfterMultiAdd]) extends AnyVal {
//  def addZIOEnv[R: ZEnvConstructor, E >: DottyNothing: Tag, I <: T: Tag](effect: ZIO[R, E, I])(implicit pos: CodePositionMaterializer): AfterAdd = {
//    dsl.addEffect[IO[E, _], I, IgnorableFunctoidDummyImplicit](ZEnvConstructor[R].map(effect.provideEnvironment(_)))
//  }

  inline def addZIOEnv[R: ZEnvConstructor, E >: DottyNothing: Tag, I <: T: Tag, N <: IgnorableFunctoidDummyImplicit](
    inline function: N ?=> Functoid[ZIO[R, E, I]]
  )(implicit pos: CodePositionMaterializer
  ): AfterAdd = {
    val functoid: Functoid[ZIO[R, E, I]] = DischargeDummyMacro.dischargeDummy[ZIO[R, E, I], N](function)
    dsl.addEffect[IO[E, _], I, IgnorableFunctoidDummyImplicit](functoid.map2(ZEnvConstructor[R])(_.provideEnvironment(_)))
  }

//  def addZManagedEnv[R: ZEnvConstructor, E >: DottyNothing: Tag, I <: T: Tag](resource: ZManaged[R, E, I])(implicit pos: CodePositionMaterializer): AfterAdd = {
//    dsl.addResourceAdapt(ZEnvConstructor[R].map(resource.provideEnvironment(_)))
//  }

  inline def addZManagedEnv[R: ZEnvConstructor, E >: DottyNothing: Tag, I <: T: Tag, N <: IgnorableFunctoidDummyImplicit](
    inline function: N ?=> Functoid[ZManaged[R, E, I]]
  )(implicit pos: CodePositionMaterializer
  ): AfterAdd = {
    val functoid: Functoid[ZManaged[R, E, I]] = DischargeDummyMacro.dischargeDummy[ZManaged[R, E, I], N](function)
    dsl.addResourceAdapt(functoid.map2(ZEnvConstructor[R])(_.provideEnvironment(_)))
  }

//  def addZLayerEnv[R: ZEnvConstructor, E >: DottyNothing: Tag, I <: T: Tag](layer: ZLayer[R, E, I])(implicit pos: CodePositionMaterializer): AfterAdd = {
//    dsl.addResourceAdapt(ZEnvConstructor[R].map(ZLayer.succeedEnvironment(_) >>> layer))
//  }

  inline def addZLayerEnv[R: ZEnvConstructor, E >: DottyNothing: Tag, I <: T: Tag, N <: IgnorableFunctoidDummyImplicit](
    inline function: N ?=> Functoid[ZLayer[R, E, I]]
  )(implicit pos: CodePositionMaterializer
  ): AfterAdd = {
    val functoid: Functoid[ZLayer[R, E, I]] = DischargeDummyMacro.dischargeDummy[ZLayer[R, E, I], N](function)
    dsl.addResourceAdapt(functoid.map2(ZEnvConstructor[R])((r, e) => ZLayer.succeedEnvironment(e) >>> r))
  }

  /**
    * Adds set element binding to a Lifecycle class which has a ZIO effect type that specifies dependencies via zio environment.
    *
    * Warning: removes the precise subtype of Lifecycle because of `Lifecycle.map`:
    * Integration checks on mixed-in as a trait onto a Lifecycle value result here will be lost
    */
  def addZEnvResource[R1 <: Lifecycle[ZIO[Nothing, Any, +_], T]: ClassConstructor](
    implicit tag: ZIOEnvLifecycleTag[R1, T],
    pos: CodePositionMaterializer,
  ): AfterAdd = {
    import tag.{A, E, R, ctorR, ev, resourceTag, tagFull}
    val provider = ClassConstructor[R1].map2(ctorR.provider)((r1, zenv) => provideZEnvLifecycle[R, E, A](ev(r1), zenv))(using tagFull)
    dsl.addResource(provider)(resourceTag, pos)
  }

//  /**
//    * Adds set element binding to a Lifecycle value which has a ZIO effect type that specifies dependencies via zio environment.
//    *
//    * Warning: removes the precise subtype of Lifecycle because of `Lifecycle.map`:
//    * Integration checks on mixed-in as a trait onto a Lifecycle value result here will be lost
//    */
//  def addZEnvResource[R: ZEnvConstructor, E >: DottyNothing: Tag, I <: T: Tag](
//    resource: Lifecycle[ZIO[R, E, _], I]
//  )(implicit pos: CodePositionMaterializer
//  ): AfterAdd = {
//    val provider = ZEnvConstructor[R].map(provideZEnvLifecycle(resource, _))
//    dsl.addResource[Lifecycle[ZIO[Any, E, _], I], IgnorableFunctoidDummyImplicit](provider)
//  }

  /**
    * Adds set element binding to a Lifecycle value which has a ZIO effect type that specifies dependencies via zio environment.
    *
    * Warning: removes the precise subtype of Lifecycle because of `Lifecycle.map`:
    * Integration checks on mixed-in as a trait onto a Lifecycle value result here will be lost
    */
  inline def addZEnvResource[R: ZEnvConstructor, E >: DottyNothing: Tag, I <: T: Tag, N <: IgnorableFunctoidDummyImplicit](
    inline function: N ?=> Functoid[Lifecycle[ZIO[R, E, _], I]]
  )(implicit pos: CodePositionMaterializer
  ): AfterAdd = {
    val functoid: Functoid[Lifecycle[ZIO[R, E, _], I]] = DischargeDummyMacro.dischargeDummy[Lifecycle[ZIO[R, E, _], I], N](function)
    val provider = functoid.map2(ZEnvConstructor[R])(provideZEnvLifecycle)
    dsl.addResource[Lifecycle[ZIO[Any, E, _], I], IgnorableFunctoidDummyImplicit](provider)
  }

  @inline private def provideZEnvLifecycle[R, E, A](lifecycle: Lifecycle[ZIO[R, E, _], A], zenv: ZEnvironment[R]): Lifecycle[ZIO[Any, E, _], A] = {
    lifecycle.mapK[ZIO[R, E, _], ZIO[Any, E, _]](Morphism1(_.provideEnvironment(zenv)))
  }
}
