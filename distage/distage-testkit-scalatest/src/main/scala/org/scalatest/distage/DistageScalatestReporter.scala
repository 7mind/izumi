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

import java.time.OffsetDateTime
import scala.annotation.unused
import scala.concurrent.duration.FiniteDuration

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

    // Every Event field below is populated from testkit data. Nothing is left to ScalaTest's
    // case-class defaults (current-thread, current-time): ScalaTest's XML pair-walking reporters
    // (JUnitXmlReporter, XmlReporter, DashboardReporter) use `event.timeStamp` arithmetic for test
    // durations and would otherwise see linearised-emission timestamps, not real measured times.
    val location = Some(LineInFile(test.test.pos.line, test.test.pos.file, None))
    val rerunner = Some(suiteClassName1)
    val terminatorFormatter = Some(getIndentedTextForTest(s"- $testName", 0, includeIcon = false))
    val infoFormatter = Some(getIndentedTextForInfo(s"- $testName", 1, includeIcon = false, infoIsInsideATest = true))
    val emptyRecordedEvents: Vector[RecordableEvent] = Vector.empty
    val emptyAnalysis: Vector[String] = Vector.empty
    val noPayload: Option[Any] = None

    def epochMs(odt: OffsetDateTime): Long = odt.toInstant.toEpochMilli

    def reportStarting(stamp: OffsetDateTime, thread: String): Unit = {
      suiteHandler.doReportEvent(suiteId1)(
        ordinal =>
          TestStarting(
            ordinal = ordinal,
            suiteName = suiteName1,
            suiteId = suiteId1.suiteId,
            suiteClassName = Some(suiteClassName1),
            testName = testName,
            testText = testName,
            formatter = Some(MotionToSuppress),
            location = location,
            rerunner = rerunner,
            payload = noPayload,
            threadName = thread,
            timeStamp = epochMs(stamp),
          )
      )
    }

    def reportFailure(duration: FiniteDuration, throwable: Throwable, trace: Exit.Trace[Any], stamp: OffsetDateTime, thread: String): Unit = {
      suiteHandler.doReportEvent(suiteId1)(
        ordinal =>
          TestFailed(
            ordinal = ordinal,
            message = Option(throwable.getMessage).getOrElse("null"),
            suiteName = suiteName1,
            suiteId = suiteId1.suiteId,
            suiteClassName = Some(suiteClassName1),
            testName = testName,
            testText = testName,
            recordedEvents = emptyRecordedEvents,
            analysis = emptyAnalysis,
            // use .toThrowable to obtain a zio.FiberFailure instead of .unsafeAttachTraceOrReturnNewThrowable because scalatest
            // does not display suppressed exceptions (which is how zio attaches trace)
            throwable = Some(trace.toThrowable),
            duration = Some(duration.toMillis),
            formatter = terminatorFormatter,
            location = location,
            rerunner = rerunner,
            payload = noPayload,
            threadName = thread,
            timeStamp = epochMs(stamp),
          )
      )
    }

    def reportCancellation(duration: FiniteDuration, clue: String, trace: Exit.Trace[Any], stamp: OffsetDateTime, thread: String): Unit = {
      suiteHandler.doReportEvent(suiteId1)(
        ordinal =>
          TestCanceled(
            ordinal = ordinal,
            message = clue,
            suiteName = suiteName1,
            suiteId = suiteId1.suiteId,
            suiteClassName = Some(suiteClassName1),
            testName = testName,
            testText = testName,
            recordedEvents = emptyRecordedEvents,
            // use .toThrowable instead of .unsafeAttachTraceOrReturnNewThrowable because scalatest
            // does not display suppressed exceptions (which is how zio attaches trace)
            throwable = Some(trace.toThrowable),
            duration = Some(duration.toMillis),
            formatter = terminatorFormatter,
            location = location,
            rerunner = rerunner,
            payload = noPayload,
            threadName = thread,
            timeStamp = epochMs(stamp),
          )
      )
    }

    def reportSucceeded(duration: FiniteDuration, stamp: OffsetDateTime, thread: String): Unit = {
      suiteHandler.doReportEvent(suiteId1)(
        ordinal =>
          TestSucceeded(
            ordinal = ordinal,
            suiteName = suiteName1,
            suiteId = suiteId1.suiteId,
            suiteClassName = Some(suiteClassName1),
            testName = testName,
            testText = testName,
            recordedEvents = emptyRecordedEvents,
            duration = Some(duration.toMillis),
            formatter = terminatorFormatter,
            location = location,
            rerunner = rerunner,
            payload = noPayload,
            threadName = thread,
            timeStamp = epochMs(stamp),
          )
      )
    }

    def reportInfo(message: String, stamp: OffsetDateTime, thread: String): Unit = {
      suiteHandler.doReportEvent(suiteId1)(
        ordinal =>
          InfoProvided(
            ordinal = ordinal,
            message = s"Test: ${test.test.id} \n$message",
            nameInfo = Some(NameInfo(suiteName1, suiteId1.suiteId, Some(suiteClassName1), Some(testName))),
            throwable = None,
            formatter = infoFormatter,
            location = location,
            payload = noPayload,
            threadName = thread,
            timeStamp = epochMs(stamp),
          )
      )
    }

    def timingEnd(t: Timing): OffsetDateTime = t.end

    testStatus match {
      case s: TestStatus.FailedInitialPlanning =>
        // Single-phase status: the planning attempt is the whole timeline.
        reportStarting(s.timing.begin, s.timing.threadName)
        reportFailure(s.timing.duration, s.throwableCause, Exit.Trace.ThrowableTrace(s.throwableCause), timingEnd(s.timing), s.timing.threadName)
      case s: TestStatus.FailedRuntimePlanning =>
        val throwable = s.failure.failure.toThrowable
        reportStarting(s.failure.timing.begin, s.failure.timing.threadName)
        reportFailure(s.failure.timing.duration, throwable, Exit.Trace.ThrowableTrace(throwable), timingEnd(s.failure.timing), s.failure.timing.threadName)
      case s: TestStatus.EarlyIgnoredByPrecondition =>
        val t = s.cause.instantiationTiming
        reportStarting(t.begin, t.threadName)
        reportCancellation(
          t.duration,
          s"ignored early: ${s.checks.toList.niceList()}",
          // the Throwable is necessary for Intellij to include explanation other than just 'Test Canceled'
          Exit.Trace.ThrowableTrace(new IntegrationCheckException(s.checks, captureStackTrace = false)),
          timingEnd(t),
          t.threadName,
        )
      case s: TestStatus.EarlyCancelled =>
        val t = s.cause.instantiationTiming
        reportStarting(t.begin, t.threadName)
        reportCancellation(t.duration, s"cancelled early: ${s.throwableCause.getMessage}", Exit.Trace.ThrowableTrace(s.throwableCause), timingEnd(t), t.threadName)
      case s: TestStatus.EarlyFailed =>
        val t = s.cause.instantiationTiming
        reportStarting(t.begin, t.threadName)
        reportFailure(t.duration, s.throwableCause, Exit.Trace.ThrowableTrace(s.throwableCause), timingEnd(t), t.threadName)
      case s: TestStatus.Instantiating =>
        if (s.logPlan) {
          reportInfo(s"Final test plan info: ${s.plan}", s.successfulPlanningTime.begin, s.successfulPlanningTime.threadName)
        }
        // TestStarting marks the point the test logically begins — earliest known phase moment.
        reportStarting(s.successfulPlanningTime.begin, s.successfulPlanningTime.threadName)
      case _: TestStatus.Running =>
        ()

      case s: TestStatus.IgnoredByPrecondition =>
        reportCancellation(
          s.cause.totalTime,
          s"ignored: ${s.checks.toList.niceList()}",
          Exit.Trace.ThrowableTrace(new IntegrationCheckException(s.checks, captureStackTrace = false)),
          s.cause.endInstant,
          s.cause.endThreadName,
        )

      case s: TestStatus.FailedPlanning =>
        reportFailure(s.timing.duration, s.failure, Exit.Trace.ThrowableTrace(s.failure), timingEnd(s.timing), s.timing.threadName)

      case s: TestStatus.Cancelled =>
        reportCancellation(s.cause.totalTime, s"cancelled: ${s.throwableCause.getMessage}", s.trace, s.cause.endInstant, s.cause.endThreadName)
      case s: TestStatus.Failed =>
        reportFailure(s.cause.totalTime, s.throwableCause, s.trace, s.cause.endInstant, s.cause.endThreadName)
      case s: TestStatus.Succeed =>
        reportSucceeded(s.result.totalTime, s.result.endInstant, s.result.endThreadName)
    }
  }

}
