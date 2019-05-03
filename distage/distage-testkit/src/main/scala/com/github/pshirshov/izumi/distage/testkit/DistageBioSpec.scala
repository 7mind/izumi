package com.github.pshirshov.izumi.distage.testkit

import distage.{TagK, TagKK}

abstract class DistageBioSpec[F[_, _]](implicit ev: TagK[F[Throwable, ?]], implicit val tagKK: TagKK[F]) extends DistageSpec[F[Throwable, ?]]
