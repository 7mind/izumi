package com.github.pshirshov.izumi.distage.testkit.services.st.dtest

import distage.TagK

trait AbstractDistageSpec[F[_]] extends TestRegistration[F] {
  implicit def tagMonoIO: TagK[F]
}
