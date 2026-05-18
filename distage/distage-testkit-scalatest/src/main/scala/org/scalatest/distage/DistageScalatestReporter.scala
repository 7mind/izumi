package org.scalatest.distage

import izumi.distage.model.exceptions.runtime.IntegrationCheckException
import izumi.distage.testkit.model.*
import izumi.distage.testkit.runner.api.TestReporter
import izumi.distage.testkit.runner.impl.services.Timing
import izumi.distage.testkit.services.scalatest.dstest.SuiteHandlerById
import izumi.functional.bio.Exit
import izumi.fundamentals.platform.strings.IzString.*
import org.scalatest.Suite.{getIndentedTextForInfo, getIndentedTextForTest}
import org.scalatest.events.*

import scala.annotation.unused

class DistageScalatestReporter(
  suiteHandler: SuiteHandlerById
) extends TestReporter {

  override def beginScope(@unused id: ScopeId): Unit = {}

  override def endScope(@unused id: ScopeId): Unit = {}

  override def beginLevel(scope: ScopeId, depth: Int, suites: List[SuiteMeta]): Unit = {}

  override def endLevel(scope: ScopeId, depth: Int, suites: List[SuiteMeta]): Unit = {}

  override def beginSuite(scope: ScopeId, depth: Int, suiteMeta: SuiteMeta): Unit = {
    // SuiteStarting & SuiteCompleted are emitted by ScalaTest's Framework around `suite.run(args)` —
    // see Framework.scala:309 (start) and :336 (complete) — so we deliberately do not duplicate them here.
  }

  override def endSuite(scope: ScopeId, depth: Int, suiteMeta: SuiteMeta): Unit = {
    // uneasy with setStatus being in reporter, but where else should it go?
    suiteHandler.doSetStatus(suiteMeta.suiteId) {
      mutStatus =>
        mutStatus.setCompleted()
    }
    // See beginSuite — ScalaTest emits SuiteCompleted itself.
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

    def reportFailure(timing: Timing, throwable: Throwable, trace: Exit.Trace[Any]): Unit = {
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
          duration = Some(timing.duration.toMillis),
          location = Some(LineInFile(test.test.pos.line, test.test.pos.file, None)),
          formatter = formatter,
          rerunner = Some(suiteClassName1),
          payload = None,
          threadName = Thread.currentThread.getName,
          timeStamp = timing.end.toInstant.toEpochMilli,
        )
      )
    }

    def reportCancellation(timing: Timing, clue: String, trace: Exit.Trace[Any]): Unit = {
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
          duration = Some(timing.duration.toMillis),
          location = Some(LineInFile(test.test.pos.line, test.test.pos.file, None)),
          formatter = formatter,
          // use .toThrowable instead of .unsafeAttachTraceOrReturnNewThrowable because scalatest
          // does not display suppressed exceptions (which is how zio attaches trace)
          throwable = Some(trace.toThrowable),
          rerunner = Some(suiteClassName1),
          payload = None,
          threadName = Thread.currentThread.getName,
          timeStamp = timing.end.toInstant.toEpochMilli,
        )
      )
    }

    def reportInfo(message: String, timing: Timing): Unit = {
      val formatter = Some(getIndentedTextForInfo(s"- $testName", 1, includeIcon = false, infoIsInsideATest = true))
      suiteHandler.doReportEvent(suiteId1)(
        InfoProvided(
          _,
          s"Test: ${test.test.id} \n$message",
          Some(NameInfo(suiteName1, suiteId1.suiteId, Some(suiteClassName1), Some(testName))),
          location = Some(LineInFile(test.test.pos.line, test.test.pos.file, None)),
          formatter = formatter,
          throwable = None,
          payload = None,
          threadName = Thread.currentThread.getName,
          timeStamp = timing.end.toInstant.toEpochMilli,
        )
      )
    }

    def reportStarting(timing: Timing): Unit = {
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
          rerunner = Some(suiteClassName1),
          payload = None,
          threadName = Thread.currentThread.getName,
          timeStamp = timing.end.toInstant.toEpochMilli,
        )
      )
    }

    def reportSucceeded(timing: Timing): Unit = {
      suiteHandler.doReportEvent(suiteId1)(
        TestSucceeded(
          _,
          suiteName1,
          suiteId1.suiteId,
          Some(suiteClassName1),
          testName,
          testName,
          recordedEvents = Vector.empty,
          duration = Some(timing.duration.toMillis),
          location = Some(LineInFile(test.test.pos.line, test.test.pos.file, None)),
          formatter = formatter,
          rerunner = Some(suiteClassName1),
          payload = None,
          threadName = Thread.currentThread.getName,
          timeStamp = timing.end.toInstant.toEpochMilli,
        )
      )
    }

    testStatus match {
      case s: TestStatus.FailedInitialPlanning =>
        reportStarting(s.timing)
        reportFailure(s.timing, s.throwableCause, Exit.Trace.ThrowableTrace(s.throwableCause))
      case s: TestStatus.FailedRuntimePlanning =>
        reportStarting(s.failure.timing)
        val throwable = s.failure.failure.toThrowable
        reportFailure(s.failure.timing, throwable, Exit.Trace.ThrowableTrace(throwable))
      case s: TestStatus.EarlyIgnoredByPrecondition =>
        reportStarting(s.cause.instantiationTiming)
        reportCancellation(
          s.cause.instantiationTiming,
          s"ignored early: ${s.checks.toList.niceList()}",
          // the Throwable is necessary for Intellij to include explanation other than just 'Test Canceled'
          Exit.Trace.ThrowableTrace(new IntegrationCheckException(s.checks, captureStackTrace = false)),
        )
      case s: TestStatus.EarlyCancelled =>
        reportStarting(s.cause.instantiationTiming)
        reportCancellation(s.cause.instantiationTiming, s"cancelled early: ${s.throwableCause.getMessage}", Exit.Trace.ThrowableTrace(s.throwableCause))
      case s: TestStatus.EarlyFailed =>
        reportStarting(s.cause.instantiationTiming)
        reportFailure(s.cause.instantiationTiming, s.throwableCause, Exit.Trace.ThrowableTrace(s.throwableCause))
      case s: TestStatus.Instantiating =>
        if (s.logPlan) {
          reportInfo(s"Final test plan info: ${s.plan}", s.successfulPlanningTime)
        }
        reportStarting(s.successfulPlanningTime)
      case _: TestStatus.Running =>
        ()

      case s: TestStatus.IgnoredByPrecondition =>
        reportCancellation(
          s.cause.testTiming,
          s"ignored: ${s.checks.toList.niceList()}",
          Exit.Trace.ThrowableTrace(new IntegrationCheckException(s.checks, captureStackTrace = false)),
        )

      case s: TestStatus.FailedPlanning =>
        reportFailure(s.timing, s.failure, Exit.Trace.ThrowableTrace(s.failure))

      case s: TestStatus.Cancelled =>
        reportCancellation(s.cause.testTiming, s"cancelled: ${s.throwableCause.getMessage}", s.trace)
      case s: TestStatus.Failed =>
        reportFailure(s.cause.testTiming, s.throwableCause, s.trace)
      case s: TestStatus.Succeed =>
        reportSucceeded(s.result.testTiming)
    }
  }

}
