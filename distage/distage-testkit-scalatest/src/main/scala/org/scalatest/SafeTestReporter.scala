package org.scalatest

import izumi.distage.testkit.services.dstest.DistageTestRunner.TestStatus.{Done, Finished, Ignored, Running}
import izumi.distage.testkit.services.dstest.DistageTestRunner.{SuiteData, TestMeta, TestReporter, TestStatus}
import izumi.fundamentals.platform.language.Quirks._
import izumi.fundamentals.platform.language.unused

import scala.collection.mutable

class SafeTestReporter(underlying: TestReporter) extends TestReporter {
  private val delayedReports = new mutable.LinkedHashMap[TestMeta, TestStatus]()
  private var runningTest: Option[TestMeta] = None

  override def onFailure(f: Throwable): Unit = synchronized {
    endAll()
    underlying.onFailure(f)
  }

  override def endAll(): Unit = synchronized {
    finish()
    underlying.endAll()
  }

  override def beginSuite(@unused id: SuiteData): Unit = {}

  override def endSuite(@unused id: SuiteData): Unit = {}

  override def testStatus(test: TestMeta, testStatus: TestStatus): Unit = synchronized {
    testStatus match {
//      case TestStatus.Scheduled => reportStatus(test, testStatus)

      case TestStatus.Running =>
        runningTest match {
          case Some(_) =>
            delayedReports.put(test, testStatus)
          case None =>
            reportStatus(test, testStatus)
            runningTest = Some(test)
        }

      case _: Ignored =>
        runningTest match {
          case None =>
            reportStatus(test, TestStatus.Running)
            reportStatus(test, testStatus)
          case Some(_) =>
            // Ignores happen before running, so an ignored test cannot be the same as `signalled`
            delayedReports.put(test, testStatus)
        }

      case _: Finished =>
        runningTest match {
          case Some(value) if value == test =>
            finishTest(test, testStatus)
            runningTest = None
            finish()
          case _ =>
            delayedReports.put(test, testStatus)
        }
    }
    ()
  }

  private def reportStatus(test: TestMeta, testStatus: TestStatus): Unit = synchronized {
    underlying.testStatus(test, testStatus)
  }

  private def finish(): Unit = {
    val finishedTests = delayedReports.collect { case (t, s: Done) => (t, s) }

    // report all finished tests
    finishedTests
      .foreach {
        case (t, s) =>
          reportStatus(t, TestStatus.Running)
          finishTest(t, s)
      }
    // switch to another test if it's already running
    delayedReports.collectFirst {
      case (t, Running) =>
        reportStatus(t, TestStatus.Running)
        runningTest = Some(t)
    }.discard()
  }

  private def finishTest(test: TestMeta, testStatus: TestStatus): Option[TestStatus] = {
    reportStatus(test, testStatus)
    delayedReports.remove(test)
  }
}
