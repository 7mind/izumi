package com.github.pshirshov.izumi.distage.testkit.st.adapter.specs

import distage.{TagK, TagKK}

@deprecated("Use dstest", "2019/Jul/18")
abstract class DistagePluginBioSpec[F[_, _]](implicit tagMonoIO: TagK[F[Throwable, ?]], implicit val tagBIO: TagKK[F]) extends DistagePluginSpec[F[Throwable, ?]]()(tagMonoIO)
