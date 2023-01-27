package com.github.pshirshov.test.plugins

import izumi.fundamentals.platform.build.ExposedTestScope
import logstage.IzLogger

@ExposedTestScope
class TestDepending(val testDep: TestDep, val testService: TestService)

@ExposedTestScope
trait TestDep

@ExposedTestScope
class TestDep1 extends TestDep

@ExposedTestScope
class TestDep2 extends TestDep

@ExposedTestScope
class TestDep3 extends TestDep

@ExposedTestScope
class TestService(
  val testConf: TestConf,
  val log: IzLogger,
)

@ExposedTestScope
final case class TestConf(int: Int)
