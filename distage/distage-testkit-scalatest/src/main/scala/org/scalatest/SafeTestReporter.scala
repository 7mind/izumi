package org.scalatest

import izumi.distage.testkit.services.dstest.DistageTestRunner.TestStatus.{Done, Running}
import izumi.distage.testkit.services.dstest.DistageTestRunner.{SuiteData, TestMeta, TestReporter, TestStatus}
import izumi.fundamentals.platform.language.unused

import scala.collection.mutable

class SafeTestReporter(underlying: TestReporter) extends TestReporter {
  private val delayedReports = new mutable.LinkedHashMap[TestMeta, TestStatus]()
  private val runningTests = new mutable.HashMap[String, TestMeta]()

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

  override def testStatus(test: TestMeta, testStatus: TestStatus): Unit = synchronized {
    testStatus match {
      case TestStatus.Running =>
        runningTests.get(test.id.suiteId) match {
          case Some(_) =>
            // a test is running in this suite, delay new test
            delayedReports.put(test, testStatus)
          case None =>
            // no test is running in this suite, report this one and set it as running
            reportStatus(test, testStatus)
            runningTests(test.id.suiteId) = test
        }
      case _: Done =>
        runningTests.get(test.id.suiteId) match {
          case Some(value) if value == test =>
            // finished current running test in suite,
            // report delayed reports in suite &
            // switch to another currently running test in suite if exists
            runningTests.remove(test.id.suiteId)
            reportStatus(test, testStatus)
            finish(_.id.suiteId == test.id.suiteId)
          case Some(_) =>
            // another test is running in this suite, delay finish report
            delayedReports.put(test, testStatus)
          case None =>
            // no other test is running in this suite, report directly
            reportStatus(test, TestStatus.Running)
            reportStatus(test, testStatus)
        }
    }
    ()
  }

  private def reportStatus(test: TestMeta, testStatus: TestStatus): Unit = synchronized {
    underlying.testStatus(test, testStatus)
  }

  private def finish(predicate: TestMeta => Boolean): Unit = synchronized {
    val finishedTests = delayedReports.collect { case (t, s: Done) if predicate(t) => (t, s) }

    // report all finished tests
    finishedTests
      .foreach {
        case (t, s) =>
          reportStatus(t, TestStatus.Running)
          reportStatus(t, s)
          delayedReports.remove(t)
      }
    // switch to another test if it's already running
    delayedReports.toList.foreach {
      case (t, Running) if predicate(t) && !runningTests.contains(t.id.suiteId) =>
        runningTests(t.id.suiteId) = t
        reportStatus(t, TestStatus.Running)
        delayedReports.remove(t)
      case _ =>
    }
  }
}
