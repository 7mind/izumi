package izumi.distage.modules.typeclass

import cats.Parallel
import cats.effect.kernel.Async
import distage.ModuleDef
import zio.{Runtime, Task, ZEnv}

object ZIOCatsEffectInstancesModule extends ZIOCatsEffectInstancesModule

/**
  * Adds `cats-effect` typeclass instances for ZIO
  *
  * Depends on `zio.Runtime[Any]` and `ThreadPoolExecutor @Id("zio.io")` (both can be found in [[izumi.distage.modules.support.ZIOSupportModule]])
  *
  * Will also add the following components:
  *   - [[cats.effect.Blocker]] by using `ThreadPoolExecutor @Id("zio.io")`
  */
trait ZIOCatsEffectInstancesModule extends ModuleDef {
  include(CatsEffectInstancesModule[Task])

  make[Async[Task]].from {
    (r: Runtime[ZEnv]) =>
      zio.interop.catz.asyncRuntimeInstance[Any](r)
  }
  make[Parallel[Task]].from {
    zio.interop.catz.parallelInstance[Any, Throwable]
  }
}
