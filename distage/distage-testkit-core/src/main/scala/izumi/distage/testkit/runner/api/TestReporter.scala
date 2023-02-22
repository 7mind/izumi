package izumi.distage.testkit.runner.api

import izumi.distage.testkit.model.{FullMeta, SuiteMeta, TestStatus}

trait TestReporter {
  def endScope(): Unit

  def onFailure(f: Throwable): Unit

  def beginSuite(id: SuiteMeta): Unit

  def endSuite(id: SuiteMeta): Unit

  def testStatus(meta: FullMeta, testStatus: TestStatus): Unit

  def testInfo(meta: FullMeta, message: String): Unit
}

object TestReporter {
  case class Scope()
}
