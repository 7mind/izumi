package com.github.pshirshov.test.plugins

import com.github.pshirshov.test.sneaky.SneakyPlugin
import distage.Axis
import izumi.distage.config.ConfigModuleDef

object Test extends Axis {
  case object X extends AxisChoiceDef
  case object Y extends AxisChoiceDef
}

class StaticTestPlugin extends SneakyPlugin with ConfigModuleDef {
  make[TestDep].tagged(Test.X).from[TestDep1]
  make[TestDep].tagged(Test.Y).from[TestDep2]
  make[TestService]
  make[TestConf].fromConfig("test.testconf")
}
