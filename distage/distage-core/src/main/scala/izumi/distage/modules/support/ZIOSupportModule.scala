package izumi.distage.modules.support

import distage.DIKey
import izumi.distage.modules.platform.ZIOPlatformDependentSupportModule
import izumi.functional.bio.*
import izumi.functional.bio.retry.Scheduler3
import izumi.reflect.Tag
import zio.{IO, ZEnvironment, ZIO}

object ZIOSupportModule {
  def apply[R: Tag]: ZIOSupportModule[R] = new ZIOSupportModule[R]
}

/**
  * `zio.ZIO` effect type support for `distage` resources, effects, roles & tests
  *
  *  - Adds [[izumi.functional.quasi.QuasiIO]] instances to support using ZIO in `Injector`, `distage-framework` & `distage-testkit-scalatest`
  *  - Adds [[izumi.functional.bio]] typeclass instances for ZIO
  *
  * Will also add the following components:
  *   - `ExecutionContext @Id("cpu")` for CPU-bound tasks (will be used for all tasks by default by [[zio.Runtime]])
  *   - `ExecutionContext @Id("io")` for blocking IO tasks (tasks can be scheduled to it via [[izumi.functional.bio.BlockingIO2]] or [[zio.ZIO.blocking]])
  *
  * Added into scope by [[izumi.distage.modules.DefaultModule]].
  * If [[https://github.com/zio/interop-cats/ interop-cats]] library is on the classpath during compilation,
  * implicit [[izumi.distage.modules.DefaultModule.forZIOPlusCats]] will be picked up instead of [[izumi.distage.modules.DefaultModule.forZIO]]
  * and will add a module with `cats-effect` instances [[izumi.distage.modules.typeclass.ZIOCatsEffectInstancesModule]]
  *
  * Bindings to the same keys in your own [[izumi.distage.model.definition.ModuleDef]] or plugins will override these defaults.
  */
class ZIOSupportModule[R: Tag] extends ZIOPlatformDependentSupportModule[R] {
  include(
    AnyBIO3SupportModule[ZIO, R]
      // FIXME wtf
      // FIXME wtf trifunctor broken
      .--(
        Set(
          DIKey[Ask3[ZIO]],
          DIKey[MonadAsk3[ZIO]],
          DIKey[Profunctor3[ZIO]],
          DIKey[Arrow3[ZIO]],
        )
      )
  )

  // assume default environment is `Any`, otherwise let the error message guide the user here.
  make[ZEnvironment[Any]].named("zio-initial-env").fromValue(ZEnvironment.empty)

  // FIXME wtf
  addImplicit[Async3[ZIO]]
  make[Temporal3[ZIO]].from {
    implicit r: Clock3[ZIO] =>
      implicitly[Temporal3[ZIO]]
  }
  // FIXME wtf
//  addImplicit[Local3[ZIO]]
  addImplicit[Fork3[ZIO]]
  addImplicit[Primitives3[ZIO]]
  addImplicit[PrimitivesM3[ZIO]]

  make[Scheduler3[ZIO]].from {
    implicit r: Temporal3[ZIO] =>
      implicitly[Scheduler3[ZIO]]
  }

  addImplicit[TransZio[IO]]
}
