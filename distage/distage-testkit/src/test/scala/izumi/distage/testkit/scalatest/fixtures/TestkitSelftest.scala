package izumi.distage.testkit.scalatest.fixtures

import izumi.distage.testkit.scalatest.adapter.specs.DistagePluginSpec
import distage.TagK

abstract class TestkitSelftest[F[_]: TagK] extends DistagePluginSpec[F]{
  override protected def pluginPackages: Seq[String] = thisPackage
}
