package org.scalatest.distage

import izumi.distage.model.exceptions.runtime.IntegrationCheckException
import izumi.distage.testkit.model.*
import izumi.distage.testkit.runner.api.TestReporter
import izumi.distage.testkit.services.scalatest.dstest.SuiteHandlerById
import izumi.functional.bio.Exit
import izumi.fundamentals.platform.strings.IzString.*
import org.scalatest.Suite.{getIndentedTextForInfo, getIndentedTextForTest}
import org.scalatest.events.*

import scala.annotation.unused
import scala.concurrent.duration.FiniteDuration

class DistageScalatestReporter(
  suiteHandler: SuiteHandlerById
) extends TestReporter {

  override def beginScope(@unused id: ScopeId): Unit = {}

  override def endScope(@unused id: ScopeId): Unit = {}

  override def beginLevel(scope: ScopeId, depth: Int, id: SuiteMeta): Unit = {
    // Don't report SuiteStarting & SuiteCompleted events because Scalatest sends these events on its own
//    suiteHandler.doReportEvent(id.suiteId)(
//      SuiteStarting(
//        _,
//        id.suiteName,
//        id.suiteId.suiteId,
//        Some(id.suiteClassName),
//        formatter = Some(IndentedText(id.suiteName + ":", id.suiteName, 0)),
//      )
//    )
  }

  override def endLevel(scope: ScopeId, depth: Int, id: SuiteMeta): Unit = {
    println(s"!!!suite level completed $depth ${id.suiteId} trying to set status")
//    suiteHandler.doReportEvent(id.suiteId)(
//      SuiteCompleted(
//        _,
//        id.suiteName,
//        id.suiteId.suiteId,
//        Some(id.suiteClassName),
//        formatter = Some(IndentedText(id.suiteName + ":", id.suiteName, 0)),
//        duration = None,
//      )
//    )
    suiteHandler.doSetStatus(id.suiteId) {
      s =>
        s.setCompleted()
        println(s"!!! suite status set for ${id.suiteId}")
        // FIXME SHOULDN'T BE IN REPORTER ???
    }
  }

  override def testSetupStatus(scopeId: ScopeId, depth: Int, meta: FullMeta, testStatus: TestStatus.Setup): Unit = {
    this.testStatus(scopeId, depth, meta, testStatus)
  }

  override def testStatus(@unused scope: ScopeId, @unused depth: Int, test: FullMeta, testStatus: TestStatus): Unit = {
    val suiteName1 = test.suite.suiteName
    val suiteId1 = test.suite.suiteId
    val suiteClassName1 = test.suite.suiteClassName
    val testName = test.test.id.name

    val formatter = Some(getIndentedTextForTest(s"- $testName", 0, includeIcon = false))

    def reportFailure(duration: FiniteDuration, throwable: Throwable, trace: Exit.Trace[Any]): Unit = {
      suiteHandler.doReportEvent(suiteId1)(
        TestFailed(
          _,
          Option(throwable.getMessage).getOrElse("null"),
          suiteName1,
          suiteId1.suiteId,
          Some(suiteClassName1),
          testName,
          testName,
          recordedEvents = Vector.empty,
          analysis = Vector.empty,
          // use .toThrowable to obtain a zio.FiberFailure instead of .unsafeAttachTraceOrReturnNewThrowable because scalatest
          // does not display suppressed exceptions (which is how zio attaches trace)
          throwable = Some(trace.toThrowable),
          duration = Some(duration.toMillis),
          location = Some(LineInFile(test.test.pos.line, test.test.pos.file, None)),
          formatter = formatter,
        )
      )
    }

    def reportCancellation(duration: FiniteDuration, clue: String, trace: Exit.Trace[Any]): Unit = {
      suiteHandler.doReportEvent(suiteId1)(
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
          // use .toThrowable instead of .unsafeAttachTraceOrReturnNewThrowable because scalatest
          // does not display suppressed exceptions (which is how zio attaches trace)
          throwable = Some(trace.toThrowable),
        )
      )
    }

    def reportInfo(message: String): Unit = {
      val formatter = Some(getIndentedTextForInfo(s"- $testName", 1, includeIcon = false, infoIsInsideATest = true))
      suiteHandler.doReportEvent(suiteId1)(
        InfoProvided(
          _,
          s"Test: ${test.test.id} \n$message",
          Some(NameInfo(suiteName1, suiteId1.suiteId, Some(suiteClassName1), Some(testName))),
          location = Some(LineInFile(test.test.pos.line, test.test.pos.file, None)),
          formatter = formatter,
        )
      )
    }

    def reportStarting(): Unit = {
      suiteHandler.doReportEvent(suiteId1)(
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
    }

    testStatus match {
      case s: TestStatus.FailedInitialPlanning =>
        reportStarting()
        reportFailure(s.timing.duration, s.throwableCause, Exit.Trace.ThrowableTrace(s.throwableCause))
      case s: TestStatus.FailedRuntimePlanning =>
        reportStarting()
        val throwable = s.failure.failure.toThrowable
        reportFailure(s.failure.timing.duration, throwable, Exit.Trace.ThrowableTrace(throwable))
      case s: TestStatus.EarlyIgnoredByPrecondition =>
        reportStarting()
        reportCancellation(
          s.cause.instantiationTiming.duration,
          s"ignored early: ${s.checks.toList.niceList()}",
          // the Throwable is necessary for Intellij to include explanation other than just 'Test Canceled'
          Exit.Trace.ThrowableTrace(new IntegrationCheckException(s.checks, captureStackTrace = false)),
        )
      case s: TestStatus.EarlyCancelled =>
        reportStarting()
        reportCancellation(s.cause.instantiationTiming.duration, s"cancelled early: ${s.throwableCause.getMessage}", Exit.Trace.ThrowableTrace(s.throwableCause))
      case s: TestStatus.EarlyFailed =>
        reportStarting()
        reportFailure(s.cause.instantiationTiming.duration, s.throwableCause, Exit.Trace.ThrowableTrace(s.throwableCause))
      case s: TestStatus.Instantiating =>
        if (s.logPlan) {
          reportInfo(s"Final test plan info: ${s.plan}")
        }
        reportStarting()
      case _: TestStatus.Running =>
        ()

      case s: TestStatus.IgnoredByPrecondition =>
        reportCancellation(
          s.cause.totalTime,
          s"ignored: ${s.checks.toList.niceList()}",
          Exit.Trace.ThrowableTrace(new IntegrationCheckException(s.checks, captureStackTrace = false)),
        )

      case s: TestStatus.FailedPlanning =>
        reportFailure(s.timing.duration, s.failure, Exit.Trace.ThrowableTrace(s.failure))

      case s: TestStatus.Cancelled =>
        reportCancellation(s.cause.totalTime, s"cancelled: ${s.throwableCause.getMessage}", s.trace)
      case s: TestStatus.Failed =>
        reportFailure(s.cause.totalTime, s.throwableCause, s.trace)
      case s: TestStatus.Succeed =>
        suiteHandler.doReportEvent(suiteId1)(
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

}
