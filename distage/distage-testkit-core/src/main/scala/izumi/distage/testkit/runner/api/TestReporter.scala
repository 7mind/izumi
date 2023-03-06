package izumi.distage.testkit.runner.api

import izumi.distage.testkit.model.{FullMeta, ScopeId, SuiteMeta, TestStatus}

trait TestReporter {
  def beginScope(id: ScopeId): Unit

  def endScope(id: ScopeId): Unit

  def beginLevel(scope: ScopeId, depth: Int, id: SuiteMeta): Unit

  def endLevel(scope: ScopeId, depth: Int, id: SuiteMeta): Unit

  def testSetupStatus(scopeId: ScopeId, meta: FullMeta, testStatus: TestStatus.Setup): Unit

  def testStatus(scope: ScopeId, depth: Int, meta: FullMeta, testStatus: TestStatus): Unit
}
