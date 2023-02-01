package izumi.distage.modules.typeclass

import cats.Parallel
import cats.effect.kernel.Async
import distage.ModuleDef
import izumi.reflect.Tag
import zio.{Runtime, ZEnv, ZIO}

object ZIOCatsEffectInstancesModule {
  def apply[R: Tag]: ZIOCatsEffectInstancesModule[R] = new ZIOCatsEffectInstancesModule[R]
}

/**
  * Adds `cats-effect` typeclass instances for ZIO
  *
  * Depends on `zio.Runtime[Any]` and `ThreadPoolExecutor @Id("io")` (both can be found in [[izumi.distage.modules.support.ZIOSupportModule]])
  *
  * Will also add the following components:
  *   - [[cats.effect.Blocker]] by using `ThreadPoolExecutor @Id("io")`
  */
class ZIOCatsEffectInstancesModule[R: Tag] extends ModuleDef {
  include(CatsEffectInstancesModule[ZIO[R, Throwable, +_]])

  make[Async[ZIO[R, Throwable, +_]]].from {
    (r: Runtime[ZEnv]) =>
      zio.interop.catz.asyncRuntimeInstance[R](r)
  }
  make[Parallel[ZIO[R, Throwable, +_]]].from {
    zio.interop.catz.parallelInstance[R, Throwable]
  }
}
