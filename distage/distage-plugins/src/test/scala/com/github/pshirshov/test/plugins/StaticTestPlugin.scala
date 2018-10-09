package com.github.pshirshov.test.plugins

import com.github.pshirshov.izumi.distage.config.annotations.ConfPath
import com.github.pshirshov.izumi.distage.fixtures.BasicCases.BasicCase5
import com.github.pshirshov.izumi.distage.fixtures.BasicCases.BasicCase5.{TestDependency, TestImpl1}
import com.github.pshirshov.izumi.distage.plugins.PluginDef
import com.github.pshirshov.izumi.distage.plugins.merge.ConfigurablePluginMergeStrategy.PluginMergeConfig
import com.github.pshirshov.izumi.fundamentals.platform.build.ExposedTestScope
import com.github.pshirshov.izumi.fundamentals.tags.TagExpr
import com.github.pshirshov.izumi.logstage.api.IzLogger
import distage.Injector
import distage.plugins.{ConfigurablePluginMergeStrategy, PluginDef}
import org.scalatest.WordSpec

@ExposedTestScope
class StaticTestPlugin extends PluginDef {
  make[TestDep].tagged("x").from[TestDep1]
  make[TestDep].tagged("y").from[TestDep2]
  make[TestService]
}

@ExposedTestScope
class DependingPlugin extends PluginDef {
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


