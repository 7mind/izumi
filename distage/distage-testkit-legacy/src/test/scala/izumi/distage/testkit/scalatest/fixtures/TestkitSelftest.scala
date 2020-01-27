package izumi.distage.testkit.scalatest.fixtures

import distage.TagK
import scalatest.adapter.specs.DistagePluginSpec

@deprecated("Use dstest", "2019/Jul/18")
abstract class TestkitSelftest[F[_]: TagK] extends DistagePluginSpec[F]{
  override protected def pluginPackages: Seq[String] = thisPackage
}
