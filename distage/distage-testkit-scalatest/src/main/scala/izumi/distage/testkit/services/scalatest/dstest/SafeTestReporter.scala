package izumi.distage.testkit.services.scalatest.dstest

import izumi.distage.testkit.services.dstest.DistageTestRunner.TestStatus.Done
import izumi.distage.testkit.services.dstest.DistageTestRunner.{SuiteData, TestMeta, TestReporter, TestStatus}
import izumi.distage.testkit.services.scalatest.dstest.SafeWrappedTestReporter.WrappedTestReport
import izumi.fundamentals.platform.language.unused

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

class SafeTestReporter(underlying: TestReporter) extends TestReporter {
  private val delayedReports = new mutable.LinkedHashMap[TestMeta, ArrayBuffer[WrappedTestReport]]()
  private val runningTests = new mutable.HashMap[String, TestMeta]()

  private[this] def putDelayedReport(meta: TestMeta, report: WrappedTestReport): Unit = synchronized {
    val buffer = delayedReports.getOrElseUpdate(meta, mutable.ArrayBuffer.empty)
    buffer.append(report)
    ()
  }

  override def onFailure(f: Throwable): Unit = synchronized {
    endAll()
    underlying.onFailure(f)
  }

  override def endAll(): Unit = synchronized {
    finish(_ => true)
    underlying.endAll()
  }

  override def beginSuite(@unused id: SuiteData): Unit = {}

  override def endSuite(@unused id: SuiteData): Unit = {}

  override def info(test: TestMeta, message: String): Unit = synchronized {
    delayReport(test, WrappedTestReport.Info(message))
  }

  override def testStatus(test: TestMeta, testStatus: TestStatus): Unit = {
    delayReport(test, WrappedTestReport.Status(testStatus))
  }

  private[this] def delayReport(test: TestMeta, testReport: WrappedTestReport): Unit = synchronized {
    (runningTests.get(test.id.suiteId), testReport) match {
      // if some test is running but execution of this one is done then we will finish current test
      case (Some(_), WrappedTestReport.Status(_: Done)) =>
        runningTests.remove(test.id.suiteId)
        putDelayedReport(test, testReport)
        finish(_ == test)
      case (Some(_), _) =>
        putDelayedReport(test, testReport)
      case (None, _) =>
        runningTests.put(test.id.suiteId, test)
        putDelayedReport(test, testReport)
    }
  }

  private def reportStatus(test: TestMeta, reportType: WrappedTestReport): Unit = synchronized {
    reportType match {
      case WrappedTestReport.Info(message) => underlying.info(test, message)
      case WrappedTestReport.Status(status) => underlying.testStatus(test, status)
    }
  }

  private def finish(predicate: TestMeta => Boolean): Unit = synchronized {
    val toReport = delayedReports.toList.collect {
      case (t, delayed) if predicate(t) && delayed.exists {
            case WrappedTestReport.Status(_: Done) => true
            case _ => false
          } =>
        (t, delayed)
    }
    toReport.foreach { case (t, delayed) => reportDelayed(t, delayed.toList) }

    // switch to another test if it's already running
    delayedReports.toList.foreach {
      case (t, delayed) if predicate(t) && !runningTests.contains(t.id.suiteId) && delayed.contains(WrappedTestReport.Status(TestStatus.Running)) =>
        runningTests(t.id.suiteId) = t
        reportDelayed(t, delayed.toList)
      case _ =>
    }
  }

  private def reportDelayed(testMeta: TestMeta, delayed: List[WrappedTestReport]): Option[ArrayBuffer[WrappedTestReport]] = synchronized {
    val t = (WrappedTestReport.Status(TestStatus.Running) :: delayed)
      .distinct.sortBy {
        case WrappedTestReport.Status(TestStatus.Running) => 1
        case WrappedTestReport.Info(_) => 2
        case WrappedTestReport.Status(_: TestStatus.Done) => 3
      }
    t.foreach(reportStatus(testMeta, _))
    delayedReports.remove(testMeta)
  }
}

object SafeWrappedTestReporter {
  sealed trait WrappedTestReport
  object WrappedTestReport {
    case class Info(message: String) extends WrappedTestReport
    case class Status(status: TestStatus) extends WrappedTestReport
  }
}