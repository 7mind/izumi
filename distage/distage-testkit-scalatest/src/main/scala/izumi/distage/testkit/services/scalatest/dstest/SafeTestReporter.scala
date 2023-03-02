package izumi.distage.testkit.services.scalatest.dstest

import izumi.distage.testkit.model.*
import izumi.distage.testkit.runner.api.TestReporter
import izumi.distage.testkit.services.scalatest.dstest.SafeWrappedTestReporter.WrappedTestReport
import izumi.fundamentals.platform.language.Quirks.Discarder

import scala.collection.mutable

class SafeTestReporter(underlying: TestReporter) extends TestReporter {
  private val delayedReports = new mutable.LinkedHashMap[FullMeta, mutable.Queue[WrappedTestReport]]()
  private val runningSuites = new mutable.HashMap[SuiteId, FullMeta]()

  override def beginScope(id: ScopeId): Unit = synchronized {
    underlying.beginScope(id)
  }
  override def endScope(id: ScopeId): Unit = synchronized {
    finish(_ => true)
    underlying.endScope(id)
  }

  override def beginLevel(scope: ScopeId, depth: Int, id: SuiteMeta): Unit = {
    (scope, depth, id).discard()
  }

  override def endLevel(scope: ScopeId, depth: Int, id: SuiteMeta): Unit = {
    if (depth == 0) {
      finish(_.test.id.suite == id.suiteId)
    }
  }

  override def testStatus(test: FullMeta, testStatus: TestStatus): Unit = {
    delayReport(test, WrappedTestReport.Status(testStatus))
  }

  private[this] def putDelayedReport(meta: FullMeta, report: WrappedTestReport): Unit = synchronized {
    val buffer = delayedReports.getOrElseUpdate(meta, mutable.Queue.empty)
    buffer.enqueue(report)
    ()
  }

  private[this] def delayReport(test: FullMeta, testReport: WrappedTestReport): Unit = synchronized {
    (runningSuites.get(test.test.id.suite), testReport) match {
      // if the current test locked this suite, and its execution is done
      // then we will report all tests that were finished at this point for this suite
      case (Some(t), WrappedTestReport.Status(_: TestStatus.Done)) if t == test =>
        runningSuites.remove(test.test.id.suite)
        putDelayedReport(test, testReport)
        finish(_.test.id.suite.suiteId == test.test.id.suite.suiteId)
      // if suite lock was not acquired then we should lock this suite with the current test meta
      case (None, _) =>
        runningSuites.put(test.test.id.suite, test)
        putDelayedReport(test, testReport)
      case _ =>
        putDelayedReport(test, testReport)
    }
  }

  private def reportStatus(test: FullMeta, reportType: WrappedTestReport): Unit = synchronized {
    reportType match {
      case WrappedTestReport.Status(status) => underlying.testStatus(test, status)
    }
  }

  private def finish(predicate: FullMeta => Boolean): Unit = synchronized {
    // report all tests by predicate if they were finished
    val toReport = delayedReports.toList.collect {
      case (t, delayed) if predicate(t) && delayed.exists {
            case WrappedTestReport.Status(_: TestStatus.Done) => true
            case _ => false
          } =>
        (t, delayed)
    }
    toReport.foreach { case (t, delayed) => reportDelayed(t, delayed.toList) }

    def hasRunning(q: mutable.Queue[WrappedTestReport]) = q.exists {
      case WrappedTestReport.Status(_: TestStatus.Running) => true
      case _ => false
    }

    // lock suite with another test if it's already running
    delayedReports.toList.foreach {
      case (t, delayed) if predicate(t) && !runningSuites.contains(t.test.id.suite) && hasRunning(delayed) =>
        runningSuites(t.test.id.suite) = t
      case _ =>
    }
  }

  private def reportDelayed(testMeta: FullMeta, delayed: List[WrappedTestReport]): Unit = synchronized {
    // support sequential report by sorting reports
    delayed.distinct.sortBy(_.order).foreach(reportStatus(testMeta, _))
    delayedReports.remove(testMeta)
    ()
  }

}

object SafeWrappedTestReporter {
  sealed trait WrappedTestReport {
    def order: Int
  }
  object WrappedTestReport {
    case class Status(status: TestStatus) extends WrappedTestReport {
      override def order: Int = status.order
    }
  }
}
