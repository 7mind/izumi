package org.scalatest

import izumi.distage.testkit.services.dstest.DistageTestRunner.{SuiteData, TestMeta, TestReporter, TestStatus}
import izumi.fundamentals.platform.language.Quirks
import org.scalatest.events._
import izumi.fundamentals.platform.strings.IzString._

class ScalatestReporter(args: Args, suiteName: String, suiteId: String) extends TestReporter {
  private val tracker = args.tracker

  override def onFailure(f: Throwable): Unit = {
    System.err.println("Test runner failed")
    f.printStackTrace()
  }

  override def endAll(): Unit = {}

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
    val suiteName1 = suiteName
    val suiteId1 = suiteId
    val testName = mkName(test)

    testStatus match {
      case TestStatus.Scheduled =>

      case TestStatus.Running =>
        args.reporter.apply(TestStarting(
          ord(test),
          suiteName1, suiteId1, Some(suiteId1),
          testName,
          testName,
          location = Some(LineInFile(test.pos.position.line, test.pos.position.file, None)),
        ))
      case TestStatus.Succeed(duration) =>
        args.reporter.apply(TestSucceeded(
          ord(test),
          suiteName1, suiteId1, Some(suiteId1),
          testName,
          testName,
          scala.collection.immutable.IndexedSeq.empty[RecordableEvent],
          location = Some(LineInFile(test.pos.position.line, test.pos.position.file, None)),
          duration = Some(duration.toMillis),
          rerunner = Some(test.id.suiteClassName),
        ))
      case TestStatus.Failed(t, duration) =>
        args.reporter.apply(TestFailed(
          ord(test),
          "Test failed",
          suiteName1, suiteId1, Some(suiteId1),
          testName,
          testName,
          scala.collection.immutable.IndexedSeq.empty[RecordableEvent],
          location = Some(LineInFile(test.pos.position.line, test.pos.position.file, None)),
          throwable = Some(t),
          duration = Some(duration.toMillis),
          rerunner = Some(test.id.suiteClassName),
        ))
      case TestStatus.Cancelled(clue, duration) =>
        args.reporter.apply(TestCanceled(
          ord(test),
          s"cancelled: $clue",
          suiteName1, suiteId1, Some(suiteId1),
          testName,
          testName,
          scala.collection.immutable.IndexedSeq.empty[RecordableEvent],
          location = Some(LineInFile(test.pos.position.line, test.pos.position.file, None)),
          duration = Some(duration.toMillis),
          rerunner = Some(test.id.suiteClassName),
        ))
      case TestStatus.Ignored(checks) =>
//              args.reporter.apply(TestIgnored(
//                ord(test),
//                suiteName1, suiteId1, Some(suiteId1),
//                testName,
//                testName,
//                None,
//                location = Some(LineInFile(test.pos.position.line, test.pos.position.file, None)),
//                payload = None,
//                threadName = "",
//                timeStamp = 0,
//              ))
        args.reporter.apply(TestCanceled(
          ord(test),
          s"ignored: ${checks.niceList()}",
          suiteName1, suiteId1, Some(suiteId1),
          testName,
          testName,
          scala.collection.immutable.IndexedSeq.empty[RecordableEvent],
          location = Some(LineInFile(test.pos.position.line, test.pos.position.file, None)),
          rerunner = Some(test.id.suiteClassName),
        ))
    }
  }

  private def mkName(test: TestMeta) = {
    s"${test.id.suiteName}: ${test.id.name}"
  }

  private def ord(testId: TestMeta): Ordinal = {
    Quirks.discard(testId)
    tracker.nextOrdinal()
  }
}
