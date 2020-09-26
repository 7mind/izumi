package izumi.distage.testkit.services.dstest

import distage.TagK
import izumi.distage.modules.DefaultModule

trait AbstractDistageSpec[F[_]] extends TestRegistration[F] {
  implicit def tagMonoIO: TagK[F]
  implicit def defaultModulesIO: DefaultModule[F]
}
