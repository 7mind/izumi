package izumi.distage.testkit.services.scalatest.dstest

import izumi.distage.testkit.model.*
import izumi.distage.testkit.runner.api.TestReporter
import izumi.distage.testkit.services.scalatest.dstest.ScalatestLinearizedTestReporter.{Delayed, DelayedEarly, DelayedLate}

import scala.collection.mutable

/** Serialises ScalaTest test events on a per-suite, per-test basis so that downstream
  * reporters see a strict `TestStarting → terminator → TestStarting → terminator …`
  * sequence within each suite.
  *
  * This is required by several downstream ScalaTest reporters that pair-walk events
  * within a suite and either throw `RuntimeException("unexpected …")` or otherwise
  * corrupt their output when concurrent intra-suite test events interleave (distage
  * testkit runs tests with `parallelTests = Parallelism.Unlimited` by default —
  * see `TestConfig.scala:79-82`). The exception is silently swallowed by
  * `org.scalatest.CatchReporter:34-44`, so the failure mode is "per-suite output
  * file silently absent" rather than a crash — observed as JUnit XML test undercount.
  *
  * Reporters that REQUIRE this serialisation (pair-walkers):
  *   - `org.scalatest.tools.JUnitXmlReporter.processTest:284-345` (throws on stray test events)
  *   - `org.scalatest.tools.XmlReporter:129-172, 467-469` (same pattern)
  *   - `org.scalatest.tools.DashboardReporter.SuiteRecord.toXml:720-738`
  *     and `TestRecord.addEvent:764-779` (throws on terminator without preceding start)
  *
  * Reporters that BENEFIT (better-grouped output) but do not strictly require it:
  *   - `org.scalatest.tools.HtmlReporter:1027-1083` — counter-style aggregation, tolerates
  *     interleaving but renders nicer with linearised events
  *   - Intellij's reporter (the original motivation for this class)
  *
  * Reporters that are unaffected (per-event sinks):
  *   - All `StringReporter`-family sinks (`PrintReporter`, `FileReporter`,
  *     `StandardOutReporter`, `StandardErrReporter`)
  *   - `MemoryReporter`, `FilterReporter`, `SbtDispatchReporter`,
  *     `SocketReporter`, `XmlSocketReporter`
  */
class ScalatestLinearizedTestReporter(
  underlying: TestReporter
) extends TestReporter {
  private val delayedReports = new mutable.LinkedHashMap[FullMeta, mutable.Queue[Delayed]]()
  private val runningSuites = new mutable.HashMap[SuiteId, FullMeta]()

  override def beginScope(id: ScopeId): Unit = synchronized {
    underlying.beginScope(id)
  }

  override def endScope(id: ScopeId): Unit = synchronized {
    finish(predicate = _ => true)
    underlying.endScope(id)
  }

  override def beginLevel(scope: ScopeId, depth: Int, suites: List[SuiteMeta]): Unit = {
    underlying.beginLevel(scope, depth, suites)
  }

  override def endLevel(scope: ScopeId, depth: Int, suites: List[SuiteMeta]): Unit = {
    underlying.endLevel(scope, depth, suites)
  }

  override def beginSuite(scopeId: ScopeId, depth: Int, suiteMeta: SuiteMeta): Unit = {
    underlying.beginSuite(scopeId, depth, suiteMeta)
  }

  override def endSuite(scopeId: ScopeId, depth: Int, suiteMeta: SuiteMeta): Unit = {
    finish(predicate = _.test.id.suite == suiteMeta.suiteId)
    underlying.endSuite(scopeId, depth, suiteMeta)
  }

  override def testStatus(scope: ScopeId, depth: Int, meta: FullMeta, testStatus: TestStatus): Unit = {
    delayReport(scope, Right(depth), meta, testStatus)
  }

  override def testSetupStatus(scope: ScopeId, depth: Int, meta: FullMeta, testStatus: TestStatus.Setup): Unit = {
    delayReport(scope, Left(depth), meta, testStatus)
  }

  private def delayReport(scope: ScopeId, depth: Either[Int, Int], meta: FullMeta, testReport: TestStatus): Unit = synchronized {
    (runningSuites.get(meta.test.id.suite), testReport) match {
      // if the current test locked this suite, and its execution is done
      // then we will report all tests that were finished at this point for this suite
      case (Some(t), _: TestStatus.Done) if t == meta =>
        runningSuites.remove(meta.test.id.suite)
        putDelayedReport(scope, depth, meta, testReport)
        finish(predicate = _.test.id.suite.suiteId == meta.test.id.suite.suiteId)
      // if suite lock was not acquired then we should lock this suite with the current test meta
      case (None, _) =>
        runningSuites.put(meta.test.id.suite, meta)
        putDelayedReport(scope, depth, meta, testReport)
      case _ =>
        putDelayedReport(scope, depth, meta, testReport)
    }
  }

  private def putDelayedReport(scope: ScopeId, depth: Either[Int, Int], meta: FullMeta, report: TestStatus): Unit = synchronized {
    val buffer = delayedReports.getOrElseUpdate(meta, mutable.Queue.empty)
    depth match {
      case Right(depth) =>
        buffer.enqueue(DelayedLate(scope, depth, report))

      case Left(depth) =>
        buffer.enqueue(DelayedEarly(scope, depth, report.asInstanceOf[TestStatus.Setup]))
    }

    ()
  }

  private def finish(predicate: FullMeta => Boolean): Unit = synchronized {
    def hasDone(q: mutable.Queue[Delayed]) = q.map(_.status).exists {
      case _: TestStatus.Done => true
      case _ => false
    }

    def hasRunning(q: mutable.Queue[Delayed]) = q.map(_.status).exists {
      case _: TestStatus.Running => true
      case _ => false
    }

    // report all tests by predicate if they were finished
    val toReport = delayedReports.toList.collect {
      case (t, delayed) if predicate(t) && hasDone(delayed) =>
        (t, delayed)
    }
    toReport.foreach { case (t, delayed) => reportDelayed(t, delayed.toList) }

    // lock suite with another test if it's already running
    delayedReports.toList.foreach {
      case (t, delayed) if predicate(t) && !runningSuites.contains(t.test.id.suite) && hasRunning(delayed) =>
        runningSuites(t.test.id.suite) = t
      case _ =>
    }
  }

  private def reportDelayed(testMeta: FullMeta, delayed: List[Delayed]): Unit = synchronized {
    // support sequential report by sorting reports
    delayed.distinct.sortBy(_.status.order).foreach {
      case DelayedLate(id, depth, status) =>
        underlying.testStatus(id, depth, testMeta, status)
      case DelayedEarly(id, depth, status) =>
        underlying.testSetupStatus(id, depth, testMeta, status)

    }
    delayedReports.remove(testMeta)
    ()
  }

}

object ScalatestLinearizedTestReporter {
  sealed trait Delayed {
    def id: ScopeId
    def status: TestStatus
  }
  case class DelayedLate(id: ScopeId, depth: Int, status: TestStatus) extends Delayed
  case class DelayedEarly(id: ScopeId, depth: Int, status: TestStatus.Setup) extends Delayed
}
