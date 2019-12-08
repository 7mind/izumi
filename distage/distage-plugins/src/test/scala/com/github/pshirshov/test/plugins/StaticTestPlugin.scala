package com.github.pshirshov.test.plugins

import com.github.pshirshov.test.sneaky.SneakyPlugin
import distage.ModuleDef
import izumi.distage.config.ConfigModuleDef
import izumi.distage.dsl.TestTagOps
import izumi.fundamentals.platform.build.ExposedTestScope
import izumi.logstage.api.IzLogger

@ExposedTestScope
class StaticTestPlugin extends SneakyPlugin with ConfigModuleDef {
  make[TestDep].tagged(TestTagOps("x")).from[TestDep1]
  make[TestDep].tagged(TestTagOps("y")).from[TestDep2]
  make[TestService]
  make[TestConf].fromConfig("test.testconf")
}

@ExposedTestScope
class DependingPlugin extends SneakyPlugin {
  include(DependingPlugin.module)
  make[TestDepending]
}

@ExposedTestScope
object DependingPlugin {
  val module: ModuleDef = new ModuleDef {
    // FIXME: make[] redundant generator ???
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
                   val testConf: TestConf,
                   val log: IzLogger,
                 )

@ExposedTestScope
final case class TestConf(int: Int)


