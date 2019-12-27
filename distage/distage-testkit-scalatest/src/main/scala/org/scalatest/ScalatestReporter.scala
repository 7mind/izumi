package org.scalatest

import izumi.distage.testkit.services.dstest.DistageTestRunner.{SuiteData, TestMeta, TestReporter, TestStatus}
import izumi.distage.testkit.services.scalatest.dstest.DistageTestsRegistrySingleton
import izumi.fundamentals.platform.strings.IzString._
import org.scalatest.events._

class ScalatestReporter(reporter: Reporter) extends TestReporter {

  override def onFailure(f: Throwable): Unit = {
    System.err.println("Test runner failed")
    f.printStackTrace()
  }

  override def endAll(): Unit = {}

  override def beginSuite(id: SuiteData): Unit = {
//    doReport(id.suiteId)(TestStarting(
//      _,
//      id.suiteName, id.suiteId, Some(id.suiteClassName),
//      id.suiteName,
//      id.suiteName,
//    ))
  }

  override def endSuite(id: SuiteData): Unit = {
//    doReport(id.suiteId)(TestSucceeded(
//      _,
//      id.suiteName, id.suiteId, Some(id.suiteClassName),
//      id.suiteName,
//      id.suiteName,
//      Vector.empty,
//    ))
  }

  override def testStatus(test: TestMeta, testStatus: TestStatus): Unit = {
    val suiteName1 = test.id.suiteName
    val suiteId1 = test.id.suiteId
    val suiteClassName1 = test.id.suiteId
    val testName = mkName(test)

    testStatus match {
//      case TestStatus.Scheduled =>

      case TestStatus.Running =>
        doReport(suiteId1)(TestStarting(
          _,
          suiteName1, suiteId1, Some(suiteClassName1),
          testName,
          testName,
          location = Some(LineInFile(test.pos.position.line, test.pos.position.file, None)),
          rerunner = Some(test.id.suiteClassName),
        ))
      case TestStatus.Succeed(duration) =>
        doReport(suiteId1)(TestSucceeded(
          _,
          suiteName1, suiteId1, Some(suiteClassName1),
          testName,
          testName,
          recordedEvents = Vector.empty,
          duration = Some(duration.toMillis),
          location = Some(LineInFile(test.pos.position.line, test.pos.position.file, None)),
          rerunner = Some(test.id.suiteClassName),
        ))
      case TestStatus.Failed(t, duration) =>
        doReport(suiteId1)(TestFailed(
          _,
          "Test failed",
          suiteName1, suiteId1, Some(suiteClassName1),
          testName,
          testName,
          recordedEvents = Vector.empty,
          throwable = Some(t),
          duration = Some(duration.toMillis),
          location = Some(LineInFile(test.pos.position.line, test.pos.position.file, None)),
          rerunner = Some(test.id.suiteClassName),
        ))
      case TestStatus.Cancelled(clue, duration) =>
        doReport(suiteId1)(TestCanceled(
          _,
          s"cancelled: $clue",
          suiteName1, suiteId1, Some(suiteClassName1),
          testName,
          testName,
          recordedEvents = Vector.empty,
          duration = Some(duration.toMillis),
          location = Some(LineInFile(test.pos.position.line, test.pos.position.file, None)),
          rerunner = Some(test.id.suiteClassName),
        ))
      case TestStatus.Ignored(checks) =>
        doReport(suiteId1)(TestCanceled(
          _,
          s"ignored: ${checks.niceList()}",
          suiteName1, suiteId1, Some(suiteClassName1),
          testName,
          testName,
          recordedEvents = Vector.empty,
          location = Some(LineInFile(test.pos.position.line, test.pos.position.file, None)),
          rerunner = Some(test.id.suiteClassName),
        ))
    }
  }

  private def mkName(test: TestMeta): String = {
    s"${test.id.suiteName}: ${test.id.name}"
  }

  @inline private[this] def doReport(suiteId: String)(f: Ordinal => Event): Unit = {
    DistageTestsRegistrySingleton.runReport(suiteId)(tracker => reporter(f(tracker.nextOrdinal())))
  }

}
