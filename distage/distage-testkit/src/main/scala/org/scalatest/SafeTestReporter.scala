package org.scalatest

import izumi.distage.testkit.services.dstest.DistageTestRunner.TestStatus.{Done, Finished, Ignored}
import izumi.distage.testkit.services.dstest.DistageTestRunner.{SuiteData, TestMeta, TestReporter, TestStatus}
import izumi.fundamentals.platform.language.Quirks

import scala.collection.mutable

class SafeTestReporter(underlying: TestReporter) extends TestReporter {
  private val allOpen = mutable.LinkedHashMap.empty[TestMeta, TestStatus]
  private var signalled: Option[TestMeta] = None


  override def onFailure(f: Throwable): Unit = synchronized {
    endAll()
    underlying.onFailure(f)
  }

  override def endAll(): Unit = synchronized {
    finish()
    assert(allOpen.isEmpty)
    underlying.endAll()
  }

  override def beginSuite(id: SuiteData): Unit = {
    Quirks.discard(id)
  }

  override def endSuite(id: SuiteData): Unit = {
    Quirks.discard(id)
  }


  override def testStatus(test: TestMeta, testStatus: TestStatus): Unit = synchronized {
    testStatus match {
      case TestStatus.Scheduled =>
        underlying.testStatus(test, testStatus)

      case TestStatus.Running =>
        allOpen.put(test, testStatus)
        signalled match {
          case Some(_) =>
          case None =>
            underlying.testStatus(test, testStatus)
            signalled = Some(test)
        }


      case _: Ignored =>
        if (allOpen.isEmpty) {
          underlying.testStatus(test, TestStatus.Running)
          underlying.testStatus(test, testStatus)
        } else {
          allOpen.put(test, testStatus)
        }

      case _: Finished =>
        allOpen.put(test, testStatus)
        signalled match {
          case Some(value) if value == test =>
            finishTest(test, testStatus)
          case _ =>
        }
        finish()
    }
  }

  private def finish(): Unit = {
    val finishedTests = allOpen.collect({ case (t, s: Done) => (t, s) })

    finishedTests
      .foreach {
        case (t, s) =>
          underlying.testStatus(t, TestStatus.Running)
          finishTest(t, s)
      }
  }

  private def finishTest(test: TestMeta, testStatus: TestStatus): Option[TestStatus] = {
    underlying.testStatus(test, testStatus)
    allOpen.remove(test)
  }
}
