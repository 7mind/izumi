package izumi.distage.testkit.runner.di

import cats.effect.IO
import izumi.distage.plugins.PluginConfig
import izumi.distage.testkit.model.TestConfig
import izumi.distage.testkit.protocol.*
import izumi.distage.testkit.runner.*
import izumi.distage.testkit.runner.spec.{Spec1, Spec2, SpecIdentity, SpecZIO, TestAssertions}
import izumi.distage.testkit.spec.TestConfiguration
import izumi.fundamentals.assertions.AssertionFailure

import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.{ExecutionContext, Future}

private[di] object SpecAssertionFixtures {
  private final val TestsPerSuite = 4
  private final class Counters {
    val bodies = new AtomicInteger(0)
    def body(): Unit = { val _ = bodies.incrementAndGet(); () }
  }
  private trait Configured extends TestConfiguration {
    override protected def config: TestConfig = TestConfig.empty.copy(pluginConfig = PluginConfig.empty)
  }
  private final class IdentitySuite(counters: Counters) extends SpecIdentity with Configured {
    "utilities" in { counters.body(); utilities(this) }
    "failure" in failing(this, counters)
    "cancellation" in cancelling(this, counters)
    "assumption" in { counters.body(); assume(false, "unavailable assumption") }
  }
  private final class UnarySuite(counters: Counters) extends Spec1[IO] with Configured {
    "utilities" in { counters.body(); utilities(this) }
    "failure" in failing(this, counters)
    "cancellation" in cancelling(this, counters)
    "assumption" in { counters.body(); assume(false, "unavailable assumption") }
  }
  private final class BifunctorSuite(counters: Counters) extends Spec2[zio.IO] with Configured {
    "utilities" in { counters.body(); utilities(this) }
    "failure" in failing(this, counters)
    "cancellation" in cancelling(this, counters)
    "assumption" in { counters.body(); assume(false, "unavailable assumption") }
  }
  private final class EnvironmentSuite(counters: Counters) extends SpecZIO with Configured {
    "utilities" in { counters.body(); utilities(this) }
    "failure" in failing(this, counters)
    "cancellation" in cancelling(this, counters)
    "assumption" in { counters.body(); assume(false, "unavailable assumption") }
  }

  private def failing(assertions: TestAssertions, counters: Counters): Unit = {
    counters.body()
    assertions.fail("expected failure")
  }

  private def cancelling(assertions: TestAssertions, counters: Counters): Unit = {
    counters.body()
    assertions.cancel("unavailable fixture")
  }

  private def utilities(assertions: TestAssertions): Unit = {
    val original = new IllegalArgumentException("expected exception")
    val caught = assertions.intercept[IllegalArgumentException](throw original)
    assertions.assert(caught eq original)
    assertions.assertThrows[IllegalArgumentException](throw original)
    assertions.assertCompiles("val value: Int = 1")
    assertions.assertDoesNotCompile("val value: String = 1")
    assertions.assertTypeError("val value: String = 1")
    val failure = assertions.intercept[AssertionFailure](assertions.fail(original))
    assertions.assert(failure.getCause eq original)
    assertions.assume(true, "available fixture")
  }

  private final class RecordingSink extends EventSink {
    private var events = Vector.empty[ProtocolMessage.Event]
    override def accept(event: ProtocolMessage.Event): Unit = synchronized { events :+= event }
    def completions: Vector[TestResult] = synchronized { events.collect { case ProtocolMessage.Event(_, RunEvent.TestCompleted(_, result)) => result } }
  }

  def run(context: ExecutionContext, verify: (String, Boolean) => Unit): Future[Unit] = {
    implicit val ec: ExecutionContext = context
    val frontends = Vector[(String, Counters => TestSuite)](
      "identity" -> (counters => new IdentitySuite(counters)),
      "unary" -> (counters => new UnarySuite(counters)),
      "bifunctor" -> (counters => new BifunctorSuite(counters)),
      "environment" -> (counters => new EnvironmentSuite(counters)),
    )
    frontends.foldLeft(Future.successful(())) { case (before, (name, construct)) => before.flatMap { _ =>
      val counters = new Counters
      val sink = new RecordingSink
      val identity = CatalogueIdentity(BuildId("assertion-frontend"), BuildTargetId(name), CatalogueId("four-specs"))
      val session = new RunSession(identity, Vector(() => construct(counters)), context, sink)
      val catalogue = session.discover().fold(failure => throw new IllegalStateException(failure.message), value => value)
      verify(name + " assertion discovery leaves all four bodies suspended", catalogue.tests.size == TestsPerSuite && counters.bodies.get() == 0 && sink.completions.isEmpty)
      val request = RunRequest(identity, Selection.All, RunOverrides(Vector.empty, Vector.empty, MemoizationOverride.Inherit))
      session.execute(RunId(name), request).map { outcome =>
        val results = outcome.results.map(result => result.id.path.last -> result).toMap
        verify(name + " shared assertion utilities execute successfully", results("utilities").status == TestStatus.Succeeded)
        verify(name + " explicit failure retains structured assertion diagnostics", results("failure").status == TestStatus.Failed && results("failure").failure.exists(failure => failure.message.contains("expected failure") && structuredAssertion(failure)))
        verify(name + " explicit cancellation retains its reason", results("cancellation").status == TestStatus.Cancelled && results("cancellation").failure.exists(_.message.contains("unavailable fixture")))
        verify(name + " false assumptions cancel rather than fail", results("assumption").status == TestStatus.Cancelled && results("assumption").failure.exists(_.message.contains("unavailable assumption")))
        verify(name + " test cancellation leaves sibling execution and application cancellation independent", !outcome.cancelled && counters.bodies.get() == TestsPerSuite && outcome.results.size == TestsPerSuite)
        verify(name + " terminal callbacks preserve every outcome exactly once", sink.completions.size == TestsPerSuite && sink.completions.toSet == outcome.results.toSet)
        println("DISTAGE_ASSERTION_FRONTEND_OK name=" + name + " tests=" + outcome.results.size)
      }
    } }
  }

  private def structuredAssertion(failure: Failure): Boolean = failure.assertion.nonEmpty || failure.causes.exists(structuredAssertion) || failure.suppressed.exists(structuredAssertion)
}
