package org.scalatest.distage

import izumi.distage.framework.model.exceptions.IntegrationCheckException
import izumi.distage.testkit.services.dstest.DistageTestRunner.{SuiteData, TestMeta, TestReporter, TestStatus}
import izumi.distage.testkit.services.scalatest.dstest.DistageTestsRegistrySingleton
import izumi.fundamentals.platform.strings.IzString._
import org.scalatest.Suite.getIndentedTextForTest
import org.scalatest.events._

class DistageScalatestReporter extends TestReporter {

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
    val testName = test.id.name

    val formatter = Some(getIndentedTextForTest(s"- $testName", 0, includeIcon = false))

    testStatus match {
      case TestStatus.Running =>
        doReport(suiteId1)(
          TestStarting(
            _,
            suiteName1,
            suiteId1,
            Some(suiteClassName1),
            testName,
            testName,
            location = Some(LineInFile(test.pos.line, test.pos.file, None)),
            formatter = Some(MotionToSuppress),
          )
        )
      case TestStatus.Succeed(duration) =>
        doReport(suiteId1)(
          TestSucceeded(
            _,
            suiteName1,
            suiteId1,
            Some(suiteClassName1),
            testName,
            testName,
            recordedEvents = Vector.empty,
            duration = Some(duration.toMillis),
            location = Some(LineInFile(test.pos.line, test.pos.file, None)),
            formatter = formatter,
          )
        )
      case TestStatus.Failed(t, duration) =>
        doReport(suiteId1)(
          TestFailed(
            _,
            Option(t.getMessage).getOrElse("null"),
            suiteName1,
            suiteId1,
            Some(suiteClassName1),
            testName,
            testName,
            recordedEvents = Vector.empty,
            analysis = Vector.empty,
            throwable = Some(t),
            duration = Some(duration.toMillis),
            location = Some(LineInFile(test.pos.line, test.pos.file, None)),
            formatter = formatter,
          )
        )
      case TestStatus.Cancelled(clue, duration) =>
        doReport(suiteId1)(
          TestCanceled(
            _,
            s"cancelled: $clue",
            suiteName1,
            suiteId1,
            Some(suiteClassName1),
            testName,
            testName,
            recordedEvents = Vector.empty,
            duration = Some(duration.toMillis),
            location = Some(LineInFile(test.pos.line, test.pos.file, None)),
            formatter = formatter,
          )
        )
      case TestStatus.Ignored(checks) =>
        doReport(suiteId1)(
          TestCanceled(
            _,
            s"ignored: ${checks.niceList()}",
            suiteName1,
            suiteId1,
            Some(suiteClassName1),
            testName,
            testName,
            recordedEvents = Vector.empty,
            throwable = Some(new IntegrationCheckException(checks)),
            formatter = formatter,
            location = Some(LineInFile(test.pos.line, test.pos.file, None)),
          )
        )
    }
  }

  @inline private[this] def doReport(suiteId: String)(f: Ordinal => Event): Unit = {
    DistageTestsRegistrySingleton.runReport(suiteId)(sr => sr.reporter(f(sr.tracker.nextOrdinal())))
  }

}
