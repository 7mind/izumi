package com.github.pshirshov.test.plugins

import com.github.pshirshov.izumi.distage.config.annotations.ConfPath
import com.github.pshirshov.izumi.fundamentals.platform.build.ExposedTestScope
import com.github.pshirshov.izumi.logstage.api.IzLogger
import distage.plugins.PluginDef

@ExposedTestScope
class StaticTestPlugin extends PluginDef /*SneakyPlugin*/ {
  make[TestDep].tagged("x").from[TestDep1]
  make[TestDep].tagged("y").from[TestDep2]
  make[TestService]
}

@ExposedTestScope
class DependingPlugin extends PluginDef /*SneakyPlugin*/ {
//  make[Unit].from((_: TestDep, _: TestService) => ()) // FIXME: Provider equals prevents merge
  make[TestDepending]
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


