package com.github.pshirshov.izumi.distage.testkit.services.dstest

import distage.TagK

trait AbstractDistageSpec[F[_]] extends TestRegistration[F] {
  implicit def tagMonoIO: TagK[F]
}
