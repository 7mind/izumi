package com.github.pshirshov.izumi.distage.testkit

import distage.TagK

abstract class DistagePluginBioSpec[F[_, _]](implicit ev: TagK[F[Throwable, ?]]) extends DistagePluginSpec[F[Throwable, ?]]
