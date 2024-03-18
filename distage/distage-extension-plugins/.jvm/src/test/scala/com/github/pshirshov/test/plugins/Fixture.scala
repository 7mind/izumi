package com.github.pshirshov.test.plugins

import logstage.IzLogger

class TestDepending(val testDep: TestDep, val testService: TestService)

trait TestDep

class TestDep1 extends TestDep

class TestDep2 extends TestDep

class TestDep3 extends TestDep

class TestService(
  val testConf: TestConf,
  val log: IzLogger,
)

final case class TestConf(int: Int)
