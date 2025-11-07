package izumi.distage.testkit.services.scalatest.dstest

import izumi.distage.testkit.model.*
import izumi.distage.testkit.runner.api.TestReporter
import izumi.distage.testkit.services.scalatest.dstest.SafeIntellijTestReporter.{Delayed, DelayedEarly, DelayedLate}

import scala.collection.mutable

/**
  * This TestReporter orders test events in a way that's digestible by Intellij.
  *
  * It exists ONLY for Intellij compat
  */
class SafeIntellijTestReporter(
  underlying: TestReporter
) extends TestReporter {
  private val delayedReports = new mutable.LinkedHashMap[FullMeta, mutable.Queue[Delayed]]()
  private val runningSuites = new mutable.HashMap[SuiteId, FullMeta]()

  override def beginScope(id: ScopeId): Unit = synchronized {
    underlying.beginScope(id)
  }

  override def endScope(id: ScopeId): Unit = synchronized {
    finish(predicate = _ => true)
    underlying.endScope(id)
  }

  override def beginLevel(scope: ScopeId, depth: Int, id: SuiteMeta): Unit = {
    underlying.beginLevel(scope, depth, id)
  }

  override def endLevel(scope: ScopeId, depth: Int, id: SuiteMeta): Unit = {
    finish(predicate = _.test.id.suite == id.suiteId)
    underlying.endLevel(scope, depth, id)
  }

  override def testStatus(scope: ScopeId, depth: Int, meta: FullMeta, testStatus: TestStatus): Unit = {
    delayReport(scope, Right(depth), meta, testStatus)
  }

  override def testSetupStatus(scope: ScopeId, depth: Int, meta: FullMeta, testStatus: TestStatus.Setup): Unit = {
    delayReport(scope, Left(depth), meta, testStatus)
  }

  private def delayReport(scope: ScopeId, depth: Either[Int, Int], meta: FullMeta, testReport: TestStatus): Unit = synchronized {
    (runningSuites.get(meta.test.id.suite), testReport) match {
      // if the current test locked this suite, and its execution is done
      // then we will report all tests that were finished at this point for this suite
      case (Some(t), _: TestStatus.Done) if t == meta =>
        runningSuites.remove(meta.test.id.suite)
        putDelayedReport(scope, depth, meta, testReport)
        finish(predicate = _.test.id.suite.suiteId == meta.test.id.suite.suiteId)
      // if suite lock was not acquired then we should lock this suite with the current test meta
      case (None, _) =>
        runningSuites.put(meta.test.id.suite, meta)
        putDelayedReport(scope, depth, meta, testReport)
      case _ =>
        putDelayedReport(scope, depth, meta, testReport)
    }
  }

  private def putDelayedReport(scope: ScopeId, depth: Either[Int, Int], meta: FullMeta, report: TestStatus): Unit = synchronized {
    val buffer = delayedReports.getOrElseUpdate(meta, mutable.Queue.empty)
    depth match {
      case Right(depth) =>
        buffer.enqueue(DelayedLate(scope, depth, report))

      case Left(depth) =>
        buffer.enqueue(DelayedEarly(scope, depth, report.asInstanceOf[TestStatus.Setup]))
    }

    ()
  }

  private def finish(predicate: FullMeta => Boolean): Unit = synchronized {
    def hasDone(q: mutable.Queue[Delayed]) = q.map(_.status).exists {
      case _: TestStatus.Done => true
      case _ => false
    }

    def hasRunning(q: mutable.Queue[Delayed]) = q.map(_.status).exists {
      case _: TestStatus.Running => true
      case _ => false
    }

    // report all tests by predicate if they were finished
    val toReport = delayedReports.toList.collect {
      case (t, delayed) if predicate(t) && hasDone(delayed) =>
        (t, delayed)
    }
    toReport.foreach { case (t, delayed) => reportDelayed(t, delayed.toList) }

    // lock suite with another test if it's already running
    delayedReports.toList.foreach {
      case (t, delayed) if predicate(t) && !runningSuites.contains(t.test.id.suite) && hasRunning(delayed) =>
        runningSuites(t.test.id.suite) = t
      case _ =>
    }
  }

  private def reportDelayed(testMeta: FullMeta, delayed: List[Delayed]): Unit = synchronized {
    // support sequential report by sorting reports
    delayed.distinct.sortBy(_.status.order).foreach {
      case DelayedLate(id, depth, status) =>
        underlying.testStatus(id, depth, testMeta, status)
      case DelayedEarly(id, depth, status) =>
        underlying.testSetupStatus(id, depth, testMeta, status)

    }
    delayedReports.remove(testMeta)
    ()
  }

}

object SafeIntellijTestReporter {
  sealed trait Delayed {
    def id: ScopeId
    def status: TestStatus
  }
  case class DelayedLate(id: ScopeId, depth: Int, status: TestStatus) extends Delayed
  case class DelayedEarly(id: ScopeId, depth: Int, status: TestStatus.Setup) extends Delayed
}
