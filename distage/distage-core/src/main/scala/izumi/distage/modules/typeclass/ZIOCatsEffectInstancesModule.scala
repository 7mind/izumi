package izumi.distage.modules.typeclass

import cats.Parallel
import cats.effect.kernel.Async
import distage.ModuleDef
import izumi.reflect.Tag
import zio.ZIO

object ZIOCatsEffectInstancesModule {
  def apply[R: Tag]: ZIOCatsEffectInstancesModule[R] = new ZIOCatsEffectInstancesModule[R]
}

/**
  * Adds `cats-effect` typeclass instances for ZIO
  */
class ZIOCatsEffectInstancesModule[R: Tag] extends ModuleDef {
  include(CatsEffectInstancesModule[ZIO[R, Throwable, +_]])

  make[Async[ZIO[R, Throwable, +_]]].from {
    zio.interop.catz.asyncInstance[R]
  }
  make[Parallel[ZIO[R, Throwable, +_]]].from {
    zio.interop.catz.parallelInstance[R, Throwable]
  }
}
