package com.github.pshirshov.izumi.distage.testkit.stest.specs

import distage.{TagK, TagKK}

abstract class DistageBioSpec[F[_, _]](implicit val tagMonoIO: TagK[F[Throwable, ?]], implicit val tagBIO: TagKK[F]) extends DistageSpec[F[Throwable, ?]]
