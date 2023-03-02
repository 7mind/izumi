package org.scalatest.distage

import izumi.distage.testkit.model.*
import izumi.distage.testkit.runner.api.TestReporter
import izumi.distage.testkit.services.scalatest.dstest.DistageTestsRegistrySingleton
import izumi.fundamentals.platform.language.Quirks.Discarder
import izumi.fundamentals.platform.strings.IzString.*
import org.scalatest.Suite.{getIndentedTextForInfo, getIndentedTextForTest}
import org.scalatest.events.*

import scala.concurrent.duration.FiniteDuration

class DistageScalatestReporter extends TestReporter {
  override def beginScope(id: ScopeId): Unit = {
    id.discard()
  }

  override def endScope(id: ScopeId): Unit = {
    id.discard()
  }

  override def beginLevel(scope: ScopeId, depth: Int, id: SuiteMeta): Unit = {
    if (depth == 0) {
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
  }

  override def endLevel(scope: ScopeId, depth: Int, id: SuiteMeta): Unit = {
    if (depth == 0) {
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
  }

  override def testStatus(test: FullMeta, testStatus: TestStatus): Unit = {
    val suiteName1 = test.suite.suiteName
    val suiteId1 = test.suite.suiteId
    val suiteClassName1 = test.suite.suiteClassName
    val testName = test.test.id.name

    val formatter = Some(getIndentedTextForTest(s"- $testName", 0, includeIcon = false))

    def reportFailure(duration: FiniteDuration, t: Throwable): Unit = {
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
          location = Some(LineInFile(test.test.pos.line, test.test.pos.file, None)),
          formatter = formatter,
        )
      )
    }

    def reportCancellation(duration: FiniteDuration, clue: String, cause: Option[Throwable]): Unit = {
      doReport(suiteId1)(
        TestCanceled(
          _,
          clue,
          suiteName1,
          suiteId1.suiteId,
          Some(suiteClassName1),
          testName,
          testName,
          recordedEvents = Vector.empty,
          duration = Some(duration.toMillis),
          location = Some(LineInFile(test.test.pos.line, test.test.pos.file, None)),
          formatter = formatter,
          throwable = cause,
        )
      )
    }

    def reportInfo(message: String): Unit = {
      val formatter = Some(getIndentedTextForInfo(s"- $testName", 1, includeIcon = false, infoIsInsideATest = true))
      doReport(suiteId1)(
        InfoProvided(
          _,
          s"Test: ${test.test.id} \n$message",
          Some(NameInfo(suiteName1, suiteId1.suiteId, Some(suiteClassName1), Some(testName))),
          location = Some(LineInFile(test.test.pos.line, test.test.pos.file, None)),
          formatter = formatter,
        )
      )
    }

    testStatus match {
      case s: TestStatus.FailedInitialPlanning =>
        reportFailure(s.timing.duration, s.throwableCause)
      case s: TestStatus.FailedRuntimePlanning =>
        reportFailure(s.failure.timing.duration, s.failure.failure.toThrowable)
      case s: TestStatus.EarlyIgnoredByPrecondition =>
        reportCancellation(s.cause.instantiationTiming.duration, s"ignored early: ${s.checks.toList.niceList()}", None)
      case s: TestStatus.EarlyCancelled =>
        reportCancellation(s.cause.instantiationTiming.duration, s"cancelled early: ${s.throwableCause.getMessage}", Some(s.throwableCause))
      case s: TestStatus.EarlyFailed =>
        reportFailure(s.cause.instantiationTiming.duration, s.throwableCause)
      case s: TestStatus.Instantiating =>
        if (s.logPlan) {
          reportInfo(s"Final test plan info: ${s.plan}")
        }
        ()
      case _: TestStatus.Running =>
        doReport(suiteId1)(
          TestStarting(
            _,
            suiteName1,
            suiteId1.suiteId,
            Some(suiteClassName1),
            testName,
            testName,
            location = Some(LineInFile(test.test.pos.line, test.test.pos.file, None)),
            formatter = Some(MotionToSuppress),
          )
        )
      case s: TestStatus.IgnoredByPrecondition =>
        reportCancellation(s.cause.totalTime, s"ignored: ${s.checks.toList.niceList()}", None)

      case s: TestStatus.FailedPlanning =>
        reportFailure(s.failure.totalTime, s.failure.failure)

      case s: TestStatus.Cancelled =>
        reportCancellation(s.cause.totalTime, s"cancelled: ${s.throwableCause.getMessage}", Some(s.throwableCause))
      case s: TestStatus.Failed =>
        reportFailure(s.cause.totalTime, s.throwableCause)
      case s: TestStatus.Succeed =>
        doReport(suiteId1)(
          TestSucceeded(
            _,
            suiteName1,
            suiteId1.suiteId,
            Some(suiteClassName1),
            testName,
            testName,
            recordedEvents = Vector.empty,
            duration = Some(s.result.totalTime.toMillis),
            location = Some(LineInFile(test.test.pos.line, test.test.pos.file, None)),
            formatter = formatter,
          )
        )

    }
  }

  @inline private[this] def doReport(suiteId: SuiteId)(f: Ordinal => Event): Unit = {
    DistageTestsRegistrySingleton.runReport(suiteId.suiteId)(sr => sr.reporter(f(sr.tracker.nextOrdinal())))
  }

}
