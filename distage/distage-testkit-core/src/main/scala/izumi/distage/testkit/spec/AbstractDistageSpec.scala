package izumi.distage.testkit.spec

import distage.TagK
import izumi.distage.modules.DefaultModule

trait AbstractDistageSpec[F[_]] extends TestConfiguration with TestRegistration[F] {
  implicit def tagMonoIO: TagK[F]
  implicit def defaultModulesIO: DefaultModule[F]
}
