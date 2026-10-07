package izumi.distage.testkit.runner

import izumi.distage.testkit.protocol.*
import izumi.distage.testkit.runner.spec.{AnyWordSpec, AsyncWordSpec, TestAssertions}
import izumi.fundamentals.assertions.{AssertionContext, AssertionFailure, CompiledText, ValueRenderer}
import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.{ExecutionContext, Future}

object FrontendAssertionFixtures {
  private trait SelfTypedRegistration { this: AnyWordSpec =>
    def markBody(): Unit
    "self-typed mixin" should { "register a body" in { markBody() } }
  }
  private final class MixedSuite(mark: () => Unit) extends AnyWordSpec with SelfTypedRegistration {
    override def markBody(): Unit = mark()
  }
  private final class CallbackSuite extends AsyncWordSpec
  private final class PaddedSuite(mark: () => Unit) extends AnyWordSpec {
    " padded scope " should { " padded case " in { mark() } }
  }

  private final class Checks extends TestAssertions {
    private val memberValue = 7

    def run(verify: (Boolean, String) => Unit): Unit = {
      val original = new IllegalStateException("original failure")
      var evaluations = 0
      val caught = intercept[IllegalStateException] { evaluations += 1; throw original }
      verify((caught eq original) && evaluations == 1, "Interception preserves the exact expected exception and evaluates once")
      val missing = intercept[AssertionFailure](intercept[IllegalStateException](()))
      verify(missing.getMessage.contains("no exception was thrown"), "Missing expected exceptions fail explicitly")
      val unexpected = intercept[AssertionFailure](intercept[IllegalArgumentException](throw original))
      verify((unexpected.getCause eq original) && unexpected.getMessage.contains("IllegalStateException"), "Unexpected exceptions retain the original cause")
      assertThrows[IllegalStateException] { evaluations += 1; throw original }
      verify(evaluations == 2, "Exception assertions evaluate their body exactly once")
      val _: Unit = succeed
      val message = intercept[AssertionFailure](fail("explicit failure"))
      verify(message.getMessage.contains("explicit failure"), "Explicit failures retain their diagnostic text")
      verify(intercept[AssertionFailure](fail()).getMessage.contains("Test failed"), "Parameterless failures carry an assertion diagnostic")
      var cancellationMessages = 0
      val stopped = intercept[TestCancelled](cancel { cancellationMessages += 1; "unsupported platform" })
      verify(stopped.getMessage == "unsupported platform" && cancellationMessages == 1, "Explicit cancellation retains its message and evaluates it once")
      var assumptionConditions = 0
      var assumptionClues = 0
      assume({ assumptionConditions += 1; true }, { assumptionClues += 1; "unused clue" })
      verify(assumptionConditions == 1 && assumptionClues == 0, "Successful assumptions evaluate the condition once and leave the clue suspended")
      val unavailable = intercept[TestCancelled](assume({ assumptionConditions += 1; false }, { assumptionClues += 1; "unavailable fixture" }))
      verify(assumptionConditions == 2 && assumptionClues == 1 && unavailable.getMessage.contains("unavailable fixture"), "False assumptions evaluate once and retain the cancellation clue")
      verify(intercept[TestCancelled](assume(false)).getMessage == "Assumption failed", "Unclued false assumptions cancel with an explicit reason")
      val caused = intercept[AssertionFailure](fail(original))
      verify(caused.getCause eq original, "Throwable failures retain the original cause")
      verify(RunnerFailure.fromThrowable(FailurePhase.Test, caused).assertion.nonEmpty, "Helper assertion failures carry structured protocol diagnostics")

      assertCompiles("val value: Int = memberValue")
      verify(memberValue == 7, "Compilation assertions see enclosing members")
      assertDoesNotCompile("val value: String = memberValue")
      val compilation = intercept[AssertionFailure](assertCompiles("val value: String = memberValue"))
      verify(compilation.getMessage.contains("String"), "Unexpected compilation failures retain compiler diagnostics")
      val accepted = intercept[AssertionFailure](assertDoesNotCompile("val value: Int = memberValue"))
      verify(accepted.getMessage.contains("Expected compilation to fail"), "Unexpected compilation success fails at runtime")
      assertDoesNotCompile("val value =")
      verify(intercept[AssertionFailure](assertCompiles("val value =")).getMessage.nonEmpty, "Syntax errors follow the same compilation assertion contract")
      assertCompiles("evaluations += 1")
      verify(evaluations == 2, "Compilation assertions never execute the checked code")

      assertTypeError("val value: String = memberValue")
      verify(memberValue == 7, "Type-error assertions see enclosing members")
      val acceptedType = intercept[AssertionFailure](assertTypeError("val value: Int = memberValue"))
      verify(acceptedType.getMessage.contains("compilation succeeded"), "Type-error assertions reject successful compilation")
      val parsedType = intercept[AssertionFailure](assertTypeError("val value ="))
      verify(parsedType.getMessage.contains("parsing failed"), "Type-error assertions reject syntax failures")
      assertTypeError("evaluations += 1; val value: String = memberValue")
      verify(evaluations == 2, "Type-error assertions never execute the checked code")

      var conditions = 0
      var clues = 0
      val clued = intercept[AssertionFailure](assert({ conditions += 1; memberValue == 8 }, { clues += 1; "custom clue" }))
      verify(conditions == 1 && clues == 1, "Clued assertions evaluate the condition and clue exactly once")
      verify(clued.getMessage.contains("custom clue"), "Clued assertions render the supplied clue")
      clued.diagnostic.source.text match {
        case CompiledText.Available(text) => verify(text.contains("memberValue"), "Clued assertions preserve the caller expression")
        case CompiledText.Unavailable => verify(clued.diagnostic.observations.nonEmpty, "Missing ranges retain expression observations")
      }
      val suppressed = new IllegalArgumentException("suppressed failure")
      caused.addSuppressed(suppressed)
      val extended = caused.withClue("retained context")
      verify((extended.getCause eq original) && extended.getSuppressed.toVector == Vector(suppressed), "Clue enrichment preserves the failure graph")
      verify(extended.getStackTrace.toVector == caused.getStackTrace.toVector && extended.diagnostic.source == caused.diagnostic.source, "Clue enrichment preserves the original stack and expression source")
      val redacted = new AssertionFailure(message.diagnostic, AssertionContext.standard.copy(valueRenderer = new ValueRenderer {
        override def render[A](value: A): String = "redacted"
      })).withClue("private clue")
      verify(redacted.getMessage.contains("redacted") && !redacted.getMessage.contains("private clue"), "Clue enrichment retains the configured value renderer")
      assert(true, AssertionContext.standard)
      val factory = implicitly[scala.collection.Factory[String, List[String]]]
      val missingFactory: scala.collection.Factory[String, List[String]] = null
      verify(factory ne null, "Reference inequality accepts universal trait values")
      verify(!(missingFactory ne null), "Reference inequality tests the underlying value rather than a conversion wrapper")
      verify(!(factory ne factory.asInstanceOf[AnyRef]), "Reference inequality preserves the underlying reference identity")
      var references = 0
      def evaluatedFactory: scala.collection.Factory[String, List[String]] = { references += 1; factory }
      verify((evaluatedFactory ne null) && references == 1, "Reference comparisons evaluate the receiver exactly once")
    }
  }

  def outcomes(identity: CatalogueIdentity, context: ExecutionContext, verify: (Boolean, String) => Unit): Future[Unit] = {
    implicit val ec: ExecutionContext = context
    val bodies = new AtomicInteger(0)
    final class OutcomeSuite extends AnyWordSpec {
      "plain failure" in { bodies.incrementAndGet(); fail() }
      "explicit cancellation" in { bodies.incrementAndGet(); cancel("unsupported platform") }
      "sibling" in { bodies.incrementAndGet(); succeed }
    }
    final class RecordingSink extends EventSink {
      private var events = Vector.empty[ProtocolMessage.Event]
      override def accept(event: ProtocolMessage.Event): Unit = synchronized { events :+= event }
      def completed: Vector[TestResult] = synchronized { events.collect { case ProtocolMessage.Event(_, RunEvent.TestCompleted(_, result)) => result } }
    }
    val sink = new RecordingSink
    val session = new RunSession(identity, Vector(() => new OutcomeSuite), context, sink)
    val catalogue = session.discover().fold(failure => throw new IllegalStateException(failure.message), value => value)
    verify(catalogue.tests.size == 3 && bodies.get() == 0, "Discovery leaves failure and cancellation bodies suspended")
    val request = RunRequest(identity, Selection.All, RunOverrides(Vector.empty, Vector.empty, MemoizationOverride.Inherit))
    session.execute(RunId("frontend-outcomes"), request).map { outcome =>
      val results = outcome.results.map(result => result.id.path.head -> result).toMap
      verify(results.view.mapValues(_.status).toMap == Map("plain failure" -> TestStatus.Failed, "explicit cancellation" -> TestStatus.Cancelled, "sibling" -> TestStatus.Succeeded), "Explicit failure and cancellation preserve each sibling's terminal status")
      def retainsAssertion(failure: Failure): Boolean =
        (failure.exceptionClass.endsWith("AssertionFailure") && failure.assertion.nonEmpty && failure.message.contains("Test failed")) || failure.causes.exists(retainsAssertion)
      verify(results("plain failure").failure.exists(retainsAssertion), "Parameterless failure retains its assertion diagnostic in the protocol cause tree")
      verify(results("explicit cancellation").failure.exists(_.message == "unsupported platform"), "Cancelled test reports retain the supplied reason")
      verify(!outcome.successful && !outcome.cancelled && outcome.failures.isEmpty, "Explicit test cancellation does not request application cancellation")
      verify(bodies.get() == 3, "Failure and cancellation bodies execute exactly once without suppressing their sibling")
      verify(sink.completed.size == 3 && sink.completed.toSet == outcome.results.toSet, "Callbacks report each failure, cancellation and successful sibling exactly once")
    }
  }

  def run(verify: (Boolean, String) => Unit): Unit = {
    new Checks().run(verify)
    var bodies = 0
    val _ = new MixedSuite(() => { bodies += 1 })
    verify(bodies == 0, "Self-typed suite mixins register through the public DSL without executing bodies")
    val inlineContext = new ExecutionContext {
      override def execute(task: Runnable): Unit = task.run()
      override def reportFailure(cause: Throwable): Unit = throw cause
    }
    val padded = new PaddedSuite(() => { bodies += 1 }).register(new RegistrationContext(BuildTargetId("padded-name-fixture"), inlineContext))
    verify(padded.tests.map(_.displayName) == Vector("padded scope should padded case"), "Plain word specifications trim scope and leaf names like the legacy frontend")
    verify(padded.tests.head.id.path == Vector("padded scope", "should", "padded case"), "Normalized word-spec identities match their display names")
    verify(bodies == 0, "Name normalization and discovery leave bodies suspended")
    val suite = new CallbackSuite
    val _ = suite.register(new RegistrationContext(BuildTargetId("serial-callback-fixture"), inlineContext))
    var order = Vector.empty[Int]
    suite.executionContext.execute(() => {
      order :+= 1
      suite.executionContext.execute(() => { order :+= 3; () })
      order :+= 2
      ()
    })
    verify(order == Vector(1, 2, 3), "Default async callbacks serialize reentrant submissions")
  }
}
