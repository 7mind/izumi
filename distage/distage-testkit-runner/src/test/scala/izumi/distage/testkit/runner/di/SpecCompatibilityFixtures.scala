package izumi.distage.testkit.runner.di

import izumi.distage.testkit.autosets.AutoSetTestkitTest
import izumi.distage.testkit.distagesuite.sequential.{DistageSequentialTestOrderingTestCIO, DistageSequentialTestOrderingTestId, DistageSequentialTestOrderingTestZIO}
import izumi.distage.testkit.protocol.*
import izumi.distage.testkit.runner.*

import scala.concurrent.{ExecutionContext, Future}

private[di] object SpecCompatibilityFixtures {
  private final val SequentialTests = 15

  def run(context: ExecutionContext, verify: (String, Boolean) => Unit): Future[Unit] = {
    implicit val ec: ExecutionContext = context
    val identity = CatalogueIdentity(BuildId("spec-compatibility"), BuildTargetId("spec-target"), CatalogueId("import-only"))
    val expected = expectedIds(identity.target)
    Vector("first", "repeated").foldLeft(Future.successful(())) { (before, name) => before.flatMap { _ =>
      var events = Vector.empty[ProtocolMessage.Event]
      val sink = new EventSink { override def accept(event: ProtocolMessage.Event): Unit = synchronized { events :+= event } }
      val factories = Vector[() => TestSuite](
        () => new AutoSetTestkitTest,
        () => new DistageSequentialTestOrderingTestId,
        () => new DistageSequentialTestOrderingTestCIO,
        () => new DistageSequentialTestOrderingTestZIO,
      )
      val session = new RunSession(identity, factories, context, sink)
      val catalogue = session.discover().fold(failure => throw new IllegalStateException(failure.message), value => value)
      verify(name + " import-only suites retain their names and registration paths", catalogue.suites.map(_.id).toSet == expected.map(_.suite) && catalogue.suites.size == factories.size && catalogue.tests.map(_.id).toSet == expected && catalogue.tests.size == expected.size)
      verify(name + " import-only discovery records source locations without reports", catalogue.tests.forall(_.location.isInstanceOf[SourceLocation.Known]) && events.isEmpty)
      val request = RunRequest(identity, Selection.All, RunOverrides(Vector.empty, Vector.empty, MemoizationOverride.Inherit))
      val resolved = session.resolve(request).fold(failure => throw new IllegalStateException(failure.message), value => value)
      verify(name + " import-only resolution retains the discovered identities", resolved.tests.map(_.id) == catalogue.tests.map(_.id) && events.isEmpty)
      session.execute(RunId(name), request).map { outcome =>
        verify(name + " import-only autoset and effect ordering bodies pass", outcome.successful && outcome.failures.isEmpty && outcome.results.size == expected.size && outcome.results.forall(_.status == TestStatus.Succeeded))
        val started = events.collect { case ProtocolMessage.Event(_, RunEvent.TestStarted(_, test)) => test }
        val completed = events.collect { case ProtocolMessage.Event(_, RunEvent.TestCompleted(_, result)) => result.id }
        verify(name + " import-only execution reports each logical identity once", started.size == expected.size && started.toSet == expected && completed.size == expected.size && completed.toSet == expected && outcome.results.map(_.id).toSet == expected)
        verify(name + " import-only run reports one terminal outcome", events.collect { case ProtocolMessage.Event(_, RunEvent.Finished(_, result)) => result } == Vector(outcome) && events.last.event == RunEvent.Finished(outcome.run, outcome))
        verify(name + " import-only reports have contiguous event ordinals", events.map(_.sequence) == events.indices.map(_.toLong).toVector)
        println("DISTAGE_SPEC_IMPORT_ONLY name=" + name + " suites=" + catalogue.suites.size + " tests=" + catalogue.tests.size + " results=" + outcome.results.size + " successful=" + outcome.successful)
      }
    } }
  }

  private def expectedIds(target: BuildTargetId): Set[TestId] = {
    val autoset = TestId(target, SuiteId("izumi.distage.testkit.autosets.AutoSetTestkitTest"), Vector("autosets", "should", "be compatible with testkit"), None)
    val sequential = Vector("Id", "CIO", "ZIO").flatMap { effect =>
      (1 to SequentialTests).map { index =>
        TestId(target, SuiteId("izumi.distage.testkit.distagesuite.sequential.DistageSequentialTestOrderingTest" + effect), Vector("sequential tests", "should", "execute in declaration order " + index), None)
      }
    }
    (sequential :+ autoset).toSet
  }
}
