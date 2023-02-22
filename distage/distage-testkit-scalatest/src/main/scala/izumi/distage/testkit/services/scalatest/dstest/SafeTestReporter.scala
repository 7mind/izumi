package izumi.distage.testkit.services.scalatest.dstest

import izumi.distage.testkit.model.{SuiteId, SuiteMeta, TestMeta, TestStatus}
import izumi.distage.testkit.runner.api.TestReporter
import izumi.distage.testkit.services.scalatest.dstest.SafeWrappedTestReporter.WrappedTestReport

import scala.collection.mutable

class SafeTestReporter(underlying: TestReporter) extends TestReporter {
  private val delayedReports = new mutable.LinkedHashMap[TestMeta, mutable.Queue[WrappedTestReport]]()
  private val runningSuites = new mutable.HashMap[SuiteId, TestMeta]()

  override def onFailure(f: Throwable): Unit = synchronized {
    endScope()
    underlying.onFailure(f)
  }

  override def endScope(): Unit = synchronized {
    finish(_ => true)
    underlying.endScope()
  }

  override def beginSuite(id: SuiteMeta): Unit = {}

  override def endSuite(id: SuiteMeta): Unit = {
    finish(_.id.suite.suiteId == id.suiteId)
  }

  override def testInfo(test: TestMeta, message: String): Unit = synchronized {
    delayReport(test, WrappedTestReport.Info(message))
  }

  override def testStatus(test: TestMeta, testStatus: TestStatus): Unit = {
    delayReport(test, WrappedTestReport.Status(testStatus))
  }

  private[this] def putDelayedReport(meta: TestMeta, report: WrappedTestReport): Unit = synchronized {
    val buffer = delayedReports.getOrElseUpdate(meta, mutable.Queue.empty)
    buffer.enqueue(report)
    ()
  }

  private[this] def delayReport(test: TestMeta, testReport: WrappedTestReport): Unit = synchronized {
    (runningSuites.get(test.id.suite.suiteId), testReport) match {
      // if the current test locked this suite, and its execution is done
      // then we will report all tests that were finished at this point for this suite
      case (Some(t), WrappedTestReport.Status(_: TestStatus.Done)) if t == test =>
        runningSuites.remove(test.id.suite.suiteId)
        putDelayedReport(test, testReport)
        finish(_.id.suite.suiteId == test.id.suite.suiteId)
      // if suite lock was not acquired then we should lock this suite with the current test meta
      case (None, _) =>
        runningSuites.put(test.id.suite.suiteId, test)
        putDelayedReport(test, testReport)
      case _ =>
        putDelayedReport(test, testReport)
    }
  }

  private def reportStatus(test: TestMeta, reportType: WrappedTestReport): Unit = synchronized {
    reportType match {
      case WrappedTestReport.Info(message) => underlying.testInfo(test, message)
      case WrappedTestReport.Status(status) => underlying.testStatus(test, status)
    }
  }

  private def finish(predicate: TestMeta => Boolean): Unit = synchronized {
    // report all tests by predicate if they were finished
    val toReport = delayedReports.toList.collect {
      case (t, delayed) if predicate(t) && delayed.exists {
            case WrappedTestReport.Status(_: TestStatus.Done) => true
            case _ => false
          } =>
        (t, delayed)
    }
    toReport.foreach { case (t, delayed) => reportDelayed(t, delayed.toList) }

    // lock suite with another test if it's already running
    delayedReports.toList.foreach {
      case (t, delayed) if predicate(t) && !runningSuites.contains(t.id.suite.suiteId) && delayed.contains(WrappedTestReport.Status(TestStatus.Running)) =>
        runningSuites(t.id.suite.suiteId) = t
      case _ =>
    }
  }

  private def reportDelayed(testMeta: TestMeta, delayed: List[WrappedTestReport]): Unit = synchronized {
    // support sequential report by sorting reports
    (WrappedTestReport.Status(TestStatus.Running) :: delayed).distinct
      .sortBy {
        case WrappedTestReport.Status(TestStatus.Running) => 1
        case WrappedTestReport.Info(_) => 2
        case WrappedTestReport.Status(_: TestStatus.Done) => 3
      }.foreach(reportStatus(testMeta, _))
    delayedReports.remove(testMeta)
    ()
  }
}

object SafeWrappedTestReporter {
  sealed trait WrappedTestReport
  object WrappedTestReport {
    case class Info(message: String) extends WrappedTestReport
    case class Status(status: TestStatus) extends WrappedTestReport
  }
}
