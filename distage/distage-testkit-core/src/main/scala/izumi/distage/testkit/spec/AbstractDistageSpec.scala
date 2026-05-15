package izumi.distage.testkit.spec

import distage.TagKK
import izumi.distage.modules.DefaultModule

trait AbstractDistageSpec[F[+_, +_]] extends TestConfiguration with TestRegistration[F[Throwable, _]] {
  implicit def tagBIO: TagKK[F]
  implicit def defaultModulesBIO: DefaultModule[F]
}
