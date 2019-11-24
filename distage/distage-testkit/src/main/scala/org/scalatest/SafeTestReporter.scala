package org.scalatest

import izumi.distage.testkit.services.dstest.DistageTestRunner.TestStatus.FinalStatus
import izumi.distage.testkit.services.dstest.DistageTestRunner.{SuiteData, TestMeta, TestReporter, TestStatus}

import scala.collection.mutable

class SafeTestReporter(underlying: TestReporter) extends TestReporter {
  private val allOpen = mutable.LinkedHashMap.empty[TestMeta, TestStatus]

  override def beginSuite(id: SuiteData): Unit = {
    underlying.beginSuite(id)
  }


  override def endSuite(id: SuiteData): Unit = synchronized {
    finish()
    assert(allOpen.isEmpty)
    underlying.endSuite(id)
  }


  override def testStatus(test: TestMeta, testStatus: TestStatus): Unit = synchronized {
    testStatus match {
      case TestStatus.Scheduled =>
        underlying.testStatus(test, testStatus)

      case TestStatus.Running =>
        allOpen.put(test, testStatus)
        if (allOpen.size == 1) {
          underlying.testStatus(test, testStatus)
        }

      case _: FinalStatus =>
        allOpen.put(test, testStatus)
        if (allOpen.toSeq.headOption.exists(_._1 == test)) {
          allOpen.remove(test)
          underlying.testStatus(test, testStatus)
          finish()
        }
    }
  }

  private def finish(): Unit = {
    allOpen
      .collect({ case (t, s: FinalStatus) => (t, s) })
      .foreach {
        case (t, s) =>
          s match {
            case _: TestStatus.Cancelled =>
            case _: TestStatus.Succeed=>
              underlying.testStatus(t, TestStatus.Running)
            case _: TestStatus.Failed =>
          }

          finishTest(t, s)
      }
  }

  private def finishTest(test: TestMeta, testStatus: TestStatus): Option[TestStatus] = {
    underlying.testStatus(test, testStatus)
    allOpen.remove(test)
  }
}
