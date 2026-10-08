package izumi.distage.modules.typeclass

import cats.Parallel
import cats.effect.kernel.Async
import izumi.distage.model.definition.ModuleDef
import izumi.reflect.Tag
import zio.ZIO

/**
  * Adds `cats-effect` typeclass instances for ZIO
  */
class ZIOCatsEffectInstancesModule[R: Tag] extends ModuleDef {
  include(CatsEffectInstancesModule.usingAsync[ZIO[R, Throwable, +_]])

  make[ZIOCatsEffectInstancesModule.Instances]

  make[Async[ZIO[R, Throwable, +_]]].from {
    (_: ZIOCatsEffectInstancesModule.Instances).asyncInstance[R]
  }
  make[Parallel[ZIO[R, Throwable, +_]]].from {
    (_: ZIOCatsEffectInstancesModule.Instances).parallelInstance[R, Throwable]
  }
}

object ZIOCatsEffectInstancesModule {
  private[distage] final class Instances extends zio.interop.CatsEffectInstances

  def apply[R: Tag]: ZIOCatsEffectInstancesModule[R] = new ZIOCatsEffectInstancesModule[R]
}
