package com.github.pshirshov.izumi.distage.testkit.stest.specs

import distage.{TagK, TagKK}

abstract class DistagePluginBioSpec[F[_, _]](implicit tagMonoIO: TagK[F[Throwable, ?]], implicit val tagBIO: TagKK[F]) extends DistagePluginSpec[F[Throwable, ?]]()(tagMonoIO)
