package com.github.pshirshov.test.plugins

import com.github.abc.SneakyPlugin
import com.github.pshirshov.izumi.distage.config.annotations.ConfPath
import com.github.pshirshov.izumi.fundamentals.platform.build.ExposedTestScope
import com.github.pshirshov.izumi.logstage.api.IzLogger
import distage.ModuleDef

@ExposedTestScope
class StaticTestPlugin extends SneakyPlugin {
  make[TestDep].tagged("x").from[TestDep1]
  make[TestDep].tagged("y").from[TestDep2]
  make[TestService]
}

@ExposedTestScope
class DependingPlugin extends SneakyPlugin {
  include(DependingPlugin.module)
  make[TestDepending]
}

@ExposedTestScope
object DependingPlugin {
  val module: ModuleDef = new ModuleDef {
    make[Unit].from((_: TestDep, _: TestService) => ()) // FIXME: Provider equals generates conflicting exception on merge
  }
}

@ExposedTestScope
class TestDepending(val testDep: TestDep, val testService: TestService)

@ExposedTestScope
trait TestDep

@ExposedTestScope
class TestDep1 extends TestDep

@ExposedTestScope
class TestDep2 extends TestDep

@ExposedTestScope
class TestService(
                   val testConf: TestConf @ConfPath("test.testconf")
                   , val log: IzLogger
                 )

@ExposedTestScope
final case class TestConf(int: Int)


