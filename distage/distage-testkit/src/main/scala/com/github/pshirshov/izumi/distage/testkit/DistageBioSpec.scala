package com.github.pshirshov.izumi.distage.testkit

import distage.{TagK, TagKK}

@deprecated("Use dstest", "2019/Jul/18")
abstract class DistageBioSpec[F[_, _]](implicit val tagMonoIO: TagK[F[Throwable, ?]], implicit val tagBIO: TagKK[F]) extends DistageSpec[F[Throwable, ?]]
