package org.scalatest

import izumi.distage.testkit.services.dstest.DistageTestRunner.{SuiteData, TestMeta, TestReporter, TestStatus}
import izumi.fundamentals.platform.language.Quirks
import org.scalatest.events._

class ScalatestReporter(args: Args, suiteName: String, suiteId: String) extends TestReporter {
  private val tracker = args.tracker

  private def ord(testId: TestMeta): Ordinal = {
    Quirks.discard(testId)
    tracker.nextOrdinal()
  }

  def recordStart(test: TestMeta): Unit = {
    args.reporter.apply(TestStarting(
      ord(test),
      suiteName, suiteId, Some(suiteId),
      test.id.name,
      test.id.name,
      location = Some(LineInFile(test.pos.position.line, test.pos.position.file, None)),
    ))
  }

  override def beginSuite(id: SuiteData): Unit = {
    args.reporter.apply(TestStarting(
      tracker.nextOrdinal(),
      suiteName, suiteId, Some(suiteId),
      id.suiteName,
      id.suiteName,
    ))
  }

  override def endSuite(id: SuiteData): Unit = {
    args.reporter.apply(TestSucceeded(
      tracker.nextOrdinal(),
      suiteName, suiteId, Some(suiteId),
      id.suiteName,
      id.suiteName,
      scala.collection.immutable.IndexedSeq.empty[RecordableEvent],
    ))
  }

  override def testStatus(test: TestMeta, testStatus: TestStatus): Unit = {
    testStatus match {
      case TestStatus.Scheduled =>

      case TestStatus.Running =>
        recordStart(test)

      case TestStatus.Succeed(duration) =>
        args.reporter.apply(TestSucceeded(
          ord(test),
          suiteName, suiteId, Some(suiteId),
          test.id.name,
          test.id.name,
          scala.collection.immutable.IndexedSeq.empty[RecordableEvent],
          location = Some(LineInFile(test.pos.position.line, test.pos.position.file, None)),
          duration = Some(duration.toMillis),
          rerunner = Some(test.id.suiteClassName),
        ))
      case TestStatus.Failed(t, duration) =>
        args.reporter.apply(TestFailed(
          ord(test),
          "Test failed",
          suiteName, suiteId, Some(suiteId),
          test.id.name,
          test.id.name,
          scala.collection.immutable.IndexedSeq.empty[RecordableEvent],
          location = Some(LineInFile(test.pos.position.line, test.pos.position.file, None)),
          throwable = Some(t),
          duration = Some(duration.toMillis),
          rerunner = Some(test.id.suiteClassName),
        ))
      case TestStatus.Cancelled(checks) =>
        recordStart(test)
        import izumi.fundamentals.platform.strings.IzString._
        args.reporter.apply(TestCanceled(
          ord(test),
          s"ignored: ${checks.niceList()}",
          suiteName, suiteId, Some(suiteId),
          test.id.name,
          test.id.name,
          scala.collection.immutable.IndexedSeq.empty[RecordableEvent],
          location = Some(LineInFile(test.pos.position.line, test.pos.position.file, None)),
          rerunner = Some(test.id.suiteClassName),
        ))
    }
  }
}
