package izumi.fixtures.runner

import izumi.distage.testkit.protocol.*
import izumi.distage.testkit.runner.*
import izumi.distage.testkit.runner.spec.{AnyWordSpec, AsyncWordSpec}

import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.{ExecutionContext, Future}

object PublishedBaseRunnerConsumer {
  private final class ImportedAsyncSuite(bodies: AtomicInteger) extends AsyncWordSpec {
    val capturedExecutionContext: ExecutionContext = executionContext
    "published async" should {
      "retain its constructor context" in Future { val _ = bodies.incrementAndGet(); assert(bodies.get() > 0) }(using capturedExecutionContext).map(_ => ())
    }
  }

  private final class ImportedOverrideSuite(bodies: AtomicInteger, borrowedContext: ExecutionContext) extends AsyncWordSpec {
    override implicit def executionContext: ExecutionContext = borrowedContext
    "published async" should {
      "use an explicit context override" in Future { val _ = bodies.incrementAndGet(); assert(bodies.get() > 0) }
    }
  }

  def main(args: Array[String]): Unit = ConsumerPlatform.run { executionContext =>
    implicit val ec: ExecutionContext = executionContext
    val bodies = new AtomicInteger(0)
    final class ImportedSuite extends AnyWordSpec {
      "published" should {
        "synchronous body" in { val _ = bodies.incrementAndGet(); assert(bodies.get() > 0) }
        "Future body" in Future { val _ = bodies.incrementAndGet(); assert(bodies.get() > 0) }
      }
    }
    val identity = CatalogueIdentity(BuildId("consumer-build"), BuildTargetId("consumer-target"), CatalogueId("consumer-catalogue"))
    val sink = new EventSink {
      override def accept(event: ProtocolMessage.Event): Unit = {
        if (ProtocolCodec.decode(ProtocolCodec.encode(event)) != Right(event)) throw new IllegalStateException("Published runner event failed its wire round-trip")
      }
    }
    val session = new RunSession(identity, Vector(() => new ImportedSuite, () => new ImportedAsyncSuite(bodies), () => new ImportedOverrideSuite(bodies, ec)), ec, sink)
    val catalogue = session.discover().fold(failure => throw new IllegalStateException(failure.message), value => value)
    if (bodies.get() != 0 || catalogue.tests.size != 4 || !catalogue.tests.forall(_.location.isInstanceOf[SourceLocation.Known])) {
      throw new IllegalStateException("Published registration macro must discover four bodies without executing them")
    }
    val request = RunRequest(identity, Selection.All, RunOverrides(Vector.empty, Vector.empty, MemoizationOverride.Inherit))
    session.execute(RunId("published-consumer"), request).flatMap { outcome =>
      if (!outcome.successful || outcome.results.size != 4 || bodies.get() != 4) throw new IllegalStateException("Published runner did not complete all plain and async bodies")
      val evaluated = new AtomicInteger(0)
      val skipped = new AtomicInteger(0)
      final class FailingSuite extends AnyWordSpec {
        "published assertion" in {
          def left: Boolean = { val _ = evaluated.incrementAndGet(); false }
          def right: Boolean = { val _ = skipped.incrementAndGet(); true }
          assert(left && right)
        }
      }
      val failing = new RunSession(identity, Vector(() => new FailingSuite), ec, sink)
      failing.execute(RunId("published-assertion"), request).map { failed =>
        def diagnostics(failure: Failure): Vector[AssertionDiagnostic] = failure.assertion.toVector ++ failure.causes.flatMap(diagnostics)
        val diagnostic = failed.results.flatMap(_.failure).flatMap(diagnostics)
        if (failed.successful || failed.results.size != 1 || failed.results.head.status != TestStatus.Failed || diagnostic.size != 1) {
          throw new IllegalStateException("Published runner lost its structured assertion failure")
        }
        val assertion = diagnostic.head
        if (!assertion.source.identity.path.endsWith("PublishedBaseRunnerConsumer.scala") || assertion.source.span == DiagnosticSpan.Unavailable || assertion.sourceValidation != DiagnosticSourceValidation.Unavailable ||
          assertion.observations.count(_.value == ObservedValue.NotEvaluated) != 1 || assertion.omittedObservations != 0 || evaluated.get() != 1 || skipped.get() != 0) {
          throw new IllegalStateException("Published runner lost assertion metadata or changed short-circuit evaluation")
        }
        val completed = ProtocolMessage.Completed(failed)
        if (ProtocolCodec.decode(ProtocolCodec.encode(completed)) != Right(completed)) throw new IllegalStateException("Published assertion diagnostic failed its complete wire round-trip")
        println("PUBLISHED_BASE_RUNNER_CONSUMER_OK bodies=4 positions=known context=constructor+override diagnostic=structured")
      }
    }
  }
}
