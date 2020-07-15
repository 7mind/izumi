package com.github.pshirshov.test.plugins

import com.github.pshirshov.test.sneaky.SneakyPlugin
import distage.{Axis, ModuleDef}
import izumi.distage.config.ConfigModuleDef
import izumi.fundamentals.platform.build.ExposedTestScope
import izumi.logstage.api.IzLogger

object Test extends Axis {
  case object X extends AxisValueDef
  case object Y extends AxisValueDef
}

@ExposedTestScope
class StaticTestPlugin extends SneakyPlugin with ConfigModuleDef {
  make[TestDep].tagged(Test.X).from[TestDep1]
  make[TestDep].tagged(Test.Y).from[TestDep2]
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
