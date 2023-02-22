package org.scalatest.distage

import izumi.distage.model.exceptions.runtime.IntegrationCheckException
import izumi.distage.testkit.model.{SuiteId, SuiteMeta, TestMeta, TestStatus}
import izumi.distage.testkit.runner.api.TestReporter
import izumi.distage.testkit.services.scalatest.dstest.DistageTestsRegistrySingleton
import org.scalatest.Suite.{getIndentedTextForInfo, getIndentedTextForTest}
import org.scalatest.events.*
import izumi.fundamentals.platform.strings.IzString.*

class DistageScalatestReporter extends TestReporter {

  override def onFailure(f: Throwable): Unit = {
    System.err.println("Test runner failed")
    f.printStackTrace()
  }

  override def endScope(): Unit = {}

  override def beginSuite(id: SuiteMeta): Unit = {
    doReport(id.suiteId)(
      SuiteStarting(
        _,
        id.suiteName,
        id.suiteId.suiteId,
        Some(id.suiteClassName),
        formatter = Some(IndentedText(id.suiteName + ":", id.suiteName, 0)),
      )
    )
  }

  override def endSuite(id: SuiteMeta): Unit = {
    doReport(id.suiteId)(
      SuiteCompleted(
        _,
        id.suiteName,
        id.suiteId.suiteId,
        Some(id.suiteClassName),
        formatter = Some(IndentedText(id.suiteName + ":", id.suiteName, 0)),
        duration = None,
      )
    )
  }

  override def testInfo(test: TestMeta, message: String): Unit = {
    val suiteName1 = test.id.suite.suiteName
    val suiteId1 = test.id.suite.suiteId
    val suiteClassName1 = test.id.suite.suiteClassName
    val testName = test.id.name
    val formatter = Some(getIndentedTextForInfo(s"- $testName", 1, includeIcon = false, infoIsInsideATest = true))
    doReport(suiteId1)(
      InfoProvided(
        _,
        s"Test: ${test.id} \n$message",
        Some(NameInfo(suiteName1, suiteId1.suiteId, Some(suiteClassName1), Some(testName))),
        location = Some(LineInFile(test.pos.line, test.pos.file, None)),
        formatter = formatter,
      )
    )
  }

  override def testStatus(test: TestMeta, testStatus: TestStatus): Unit = {
    val suiteName1 = test.id.suite.suiteName
    val suiteId1 = test.id.suite.suiteId
    val suiteClassName1 = test.id.suite.suiteClassName
    val testName = test.id.name

    val formatter = Some(getIndentedTextForTest(s"- $testName", 0, includeIcon = false))

    testStatus match {
      case TestStatus.Running =>
        doReport(suiteId1)(
          TestStarting(
            _,
            suiteName1,
            suiteId1.suiteId,
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
            suiteId1.suiteId,
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
            suiteId1.suiteId,
            Some(suiteClassName1),
            testName,
            testName,
            recordedEvents = Vector.empty,
            analysis = Vector.empty,
            throwable = Option(t),
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
            suiteId1.suiteId,
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
            s"ignored: ${checks.toList.niceList()}",
            suiteName1,
            suiteId1.suiteId,
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

  @inline private[this] def doReport(suiteId: SuiteId)(f: Ordinal => Event): Unit = {
    DistageTestsRegistrySingleton.runReport(suiteId.suiteId)(sr => sr.reporter(f(sr.tracker.nextOrdinal())))
  }

}
