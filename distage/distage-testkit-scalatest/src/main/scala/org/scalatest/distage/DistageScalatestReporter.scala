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

    // `timeStamp` is populated from testkit's own Timing measurements rather than left to ScalaTest's
    // `(new Date).getTime` case-class default. ScalaTest's XML pair-walking reporters (JUnitXmlReporter,
    // XmlReporter, DashboardReporter) derive per-testcase duration from `terminator.timeStamp -
    // testStarting.timeStamp`, so without an explicit measured stamp those durations collapse to the
    // event-emission delta — which under the per-suite event linearizer is effectively zero. `threadName`
    // is captured at the actual event-emission site below; that's "the thread that flushed this event",
    // matching the ScalaTest default semantics, since the testkit cannot meaningfully attribute async
    // test work to a single JVM thread.
    val location = Some(LineInFile(test.test.pos.line, test.test.pos.file, None))
    val rerunner = Some(suiteClassName1)
    val terminatorFormatter = Some(getIndentedTextForTest(s"- $testName", 0, includeIcon = false))
    val infoFormatter = Some(getIndentedTextForInfo(s"- $testName", 1, includeIcon = false, infoIsInsideATest = true))
    val emptyRecordedEvents: Vector[RecordableEvent] = Vector.empty
    val emptyAnalysis: Vector[String] = Vector.empty
    val noPayload: Option[Any] = None

    def epochMs(odt: OffsetDateTime): Long = odt.toInstant.toEpochMilli

    def reportFailure(timing: Timing, throwable: Throwable, trace: Exit.Trace[Any]): Unit = {
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
            duration = Some(timing.duration.toMillis),
            formatter = terminatorFormatter,
            location = location,
            rerunner = rerunner,
            payload = noPayload,
            threadName = Thread.currentThread.getName,
            timeStamp = epochMs(timing.end),
          )
      )
    }

    def reportCancellation(timing: Timing, clue: String, trace: Exit.Trace[Any]): Unit = {
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
            duration = Some(timing.duration.toMillis),
            formatter = terminatorFormatter,
            location = location,
            rerunner = rerunner,
            payload = noPayload,
            threadName = Thread.currentThread.getName,
            timeStamp = epochMs(timing.end),
          )
      )
    }

    def reportInfo(message: String, timing: Timing): Unit = {
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
            threadName = Thread.currentThread.getName,
            timeStamp = epochMs(timing.begin),
          )
      )
    }

    def reportStarting(timing: Timing): Unit = {
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
            threadName = Thread.currentThread.getName,
            timeStamp = epochMs(timing.begin),
          )
      )
    }

    def reportSucceeded(timing: Timing): Unit = {
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
            duration = Some(timing.duration.toMillis),
            formatter = terminatorFormatter,
            location = location,
            rerunner = rerunner,
            payload = noPayload,
            threadName = Thread.currentThread.getName,
            timeStamp = epochMs(timing.end),
          )
      )
    }

    testStatus match {
      case s: TestStatus.FailedInitialPlanning =>
        reportStarting(s.timing)
        reportFailure(s.timing, s.throwableCause, Exit.Trace.ThrowableTrace(s.throwableCause))
      case s: TestStatus.FailedRuntimePlanning =>
        val throwable = s.failure.failure.toThrowable
        reportStarting(s.failure.timing)
        reportFailure(s.failure.timing, throwable, Exit.Trace.ThrowableTrace(throwable))
      case s: TestStatus.EarlyIgnoredByPrecondition =>
        val t = s.cause.instantiationTiming
        reportStarting(t)
        reportCancellation(
          t,
          s"ignored early: ${s.checks.toList.niceList()}",
          // the Throwable is necessary for Intellij to include explanation other than just 'Test Canceled'
          Exit.Trace.ThrowableTrace(new IntegrationCheckException(s.checks, captureStackTrace = false)),
        )
      case s: TestStatus.EarlyCancelled =>
        val t = s.cause.instantiationTiming
        reportStarting(t)
        reportCancellation(t, s"cancelled early: ${s.throwableCause.getMessage}", Exit.Trace.ThrowableTrace(s.throwableCause))
      case s: TestStatus.EarlyFailed =>
        val t = s.cause.instantiationTiming
        reportStarting(t)
        reportFailure(t, s.throwableCause, Exit.Trace.ThrowableTrace(s.throwableCause))
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
