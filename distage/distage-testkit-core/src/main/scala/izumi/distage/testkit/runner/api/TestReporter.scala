package izumi.distage.testkit.runner.api

import izumi.distage.testkit.model.{FullMeta, ScopeId, SuiteMeta, TestStatus}

trait TestReporter {
  def beginScope(id: ScopeId): Unit
  def endScope(id: ScopeId): Unit

  def beginLevel(scope: ScopeId, depth: Int, suites: List[SuiteMeta]): Unit
  def endLevel(scope: ScopeId, depth: Int, suites: List[SuiteMeta]): Unit

  def beginSuite(scopeId: ScopeId, depth: Int, suiteMeta: SuiteMeta): Unit
  def endSuite(scopeId: ScopeId, depth: Int, suiteMeta: SuiteMeta): Unit

  def testSetupStatus(scopeId: ScopeId, depth: Int, meta: FullMeta, testStatus: TestStatus.Setup): Unit
  def testStatus(scope: ScopeId, depth: Int, meta: FullMeta, testStatus: TestStatus): Unit
}

object TestReporter {
  private[distage] trait Noop extends TestReporter {
    override def beginScope(id: ScopeId): Unit = ()
    override def endScope(id: ScopeId): Unit = ()
    override def beginLevel(scope: ScopeId, depth: Int, suites: List[SuiteMeta]): Unit = ()
    override def endLevel(scope: ScopeId, depth: Int, suites: List[SuiteMeta]): Unit = ()
    override def beginSuite(scopeId: ScopeId, depth: Int, suiteMeta: SuiteMeta): Unit = ()
    override def endSuite(scopeId: ScopeId, depth: Int, suiteMeta: SuiteMeta): Unit = ()
    override def testSetupStatus(scopeId: ScopeId, depth: Int, meta: FullMeta, testStatus: TestStatus.Setup): Unit = ()
    override def testStatus(scope: ScopeId, depth: Int, meta: FullMeta, testStatus: TestStatus): Unit = ()
  }
}
