package izumi.distage.testkit.runner

import izumi.distage.testkit.protocol.{BuildTargetId, FailurePhase}
import izumi.distage.testkit.runner.spec.{AnyWordSpec, AsyncWordSpec, TestAssertions}
import izumi.fundamentals.assertions.{AssertionContext, AssertionFailure, CompiledText, ValueRenderer}
import scala.concurrent.ExecutionContext

object FrontendAssertionFixtures {
  private trait SelfTypedRegistration { this: AnyWordSpec =>
    def markBody(): Unit
    "self-typed mixin" should { "register a body" in { markBody() } }
  }
  private final class MixedSuite(mark: () => Unit) extends AnyWordSpec with SelfTypedRegistration {
    override def markBody(): Unit = mark()
  }
  private final class CallbackSuite extends AsyncWordSpec

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

  def run(verify: (Boolean, String) => Unit): Unit = {
    new Checks().run(verify)
    var bodies = 0
    val _ = new MixedSuite(() => { bodies += 1 })
    verify(bodies == 0, "Self-typed suite mixins register through the public DSL without executing bodies")
    val inlineContext = new ExecutionContext {
      override def execute(task: Runnable): Unit = task.run()
      override def reportFailure(cause: Throwable): Unit = throw cause
    }
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
