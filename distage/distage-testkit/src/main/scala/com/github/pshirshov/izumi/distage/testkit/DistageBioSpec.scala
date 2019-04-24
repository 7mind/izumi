package com.github.pshirshov.izumi.distage.testkit

import distage.TagK

abstract class DistageBioSpec[F[_, _]](implicit ev: TagK[F[Throwable, ?]]) extends DistageSpec[F[Throwable, ?]]
