package izumi.distage.testkit.services.dstest

import distage.TagK
import izumi.distage.effect.DefaultModules

trait AbstractDistageSpec[F[_]] extends TestRegistration[F] {
  implicit def tagMonoIO: TagK[F]
  implicit def defaultModulesIO: DefaultModules[F]
}
