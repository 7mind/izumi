package com.github.pshirshov.izumi.distage.testkit.fixtures

import com.github.pshirshov.izumi.distage.testkit.DistagePluginSpec
import distage.TagK

abstract class TestkitSelftest[F[_]: TagK] extends DistagePluginSpec[F]{
  override protected def pluginPackages: Seq[String] = thisPackage
}
