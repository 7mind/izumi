package izumi.distage.testkit.runner.api

import izumi.distage.testkit.model.{SuiteData, TestMeta, TestStatus}



trait TestReporter {
  def endScope(): Unit

  def onFailure(f: Throwable): Unit

  def beginSuite(id: SuiteData): Unit

  def endSuite(id: SuiteData): Unit

  def testStatus(test: TestMeta, testStatus: TestStatus): Unit

  def testInfo(test: TestMeta, message: String): Unit
}


object TestReporter {
  case class Scope()
}