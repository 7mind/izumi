package izumi.distage.modules.support

import izumi.distage.model.definition.ModuleDef
import izumi.distage.modules.platform.ZIOPlatformDependentSupportModule
import izumi.functional.bio._
import zio.{Has, IO, ZIO}

object ZIOSupportModule extends ZIOSupportModule

/** `zio.ZIO` effect type support for `distage` resources, effects, roles & tests
  *
  * - Adds [[izumi.distage.model.effect.QuasiIO]] instances to support using ZIO in `Injector`, `distage-framework` & `distage-testkit-scalatest`
  * - Adds [[izumi.functional.bio]] typeclass instances for ZIO
  *
  * Note: by default this module will add the following components:
  *   - `ThreadPoolExecutor @Id("zio.cpu")` for CPU-bound tasks (used by default in [[zio.Runtime]])
  *   - `ThreadPoolExecutor @Id("zio.io")` and blocking IO tasks (designated via [[izumi.functional.bio.BlockingIO]] or [[zio.blocking.blocking]])
  *   - [[scala.concurrent.ExecutionContext]] bindings with the same `@Id`
  *   - [[zio.internal.tracing.TracingConfig]] will be set to [[zio.internal.tracing.TracingConfig.enabled]] by default
  *
  * Bindings to the same keys in your own [[izumi.distage.model.definition.ModuleDef]] or plugins will override these defaults.
  */
trait ZIOSupportModule extends ModuleDef with ZIOPlatformDependentSupportModule {
  include(AnyBIO3SupportModule[ZIO])

  addImplicit[BIOAsync3[ZIO]]
  make[BIOTemporal3[ZIO]].from {
    implicit r: zio.clock.Clock =>
      implicitly[BIOTemporal3[ZIO]]
  }
  addImplicit[BIOLocal[ZIO]]
  addImplicit[BIOFork3[ZIO]]
  addImplicit[BIOPrimitives3[ZIO]]

  addImplicit[BIOTransZio[IO]]

  make[zio.clock.Clock].from(Has(_: zio.clock.Clock.Service))
  make[zio.clock.Clock.Service].from(zio.clock.Clock.Service.live)
}
