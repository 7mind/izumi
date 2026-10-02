package izumi.fundamentals.assertions

import scala.collection.mutable.ArrayBuffer

object AssertionFixtures {
  private final class Oracle {
    var checks: Int = 0
    def verify(result: Boolean, description: String): Unit = {
      if (!result) throw new IllegalStateException(description)
      checks += 1
    }
    def failure(body: => Unit): AssertionFailure = {
      val caught = try { body; None } catch { case failure: AssertionFailure => Some(failure) }
      verify(caught.nonEmpty, "Expected AssertionFailure")
      caught.get
    }
    def expectFailure(body: => Unit): Unit = {
      val _ = failure(body)
      ()
    }
  }

  private final class Probe {
    val trace: ArrayBuffer[String] = ArrayBuffer.empty
    def bool(name: String, value: Boolean): Boolean = { trace += name; value }
    def number(name: String, value: Int): Int = { trace += name; value }
  }

  def main(args: Array[String]): Unit = {
    require(args.isEmpty, "Unexpected fixture arguments")
    run("range")
  }

  def run(mode: String): Unit = {
    val oracle = new Oracle
    oracle.verify(Set("range", "point").contains(mode), "Expected explicit source-range mode")
    semantics(oracle)
    val positions = diagnostics(oracle, mode)
    rendering(oracle)
    println(s"ASSERTION_FIXTURES_OK checks=${oracle.checks} mode=$mode positions=$positions")
  }

  private def semantics(oracle: Oracle): Unit = {
    val probe = new Probe
    Assert.assert(probe.bool("success", true))
    oracle.verify(probe.trace.toVector == Vector("success"), "Success evaluates once")
    probe.trace.clear()
    def receiver: Assertions = { probe.trace += "receiver"; new Assertions {} }
    oracle.expectFailure(receiver.assert(probe.bool("condition", false)))
    oracle.verify(probe.trace.toVector == Vector("receiver", "condition"), "Assertion receiver evaluates once before the condition")
    val receiverException = new IllegalArgumentException("receiver failure")
    def throwingReceiver: Assertions = throw receiverException
    probe.trace.clear()
    val receiverCaught = try { throwingReceiver.assert(probe.bool("unreachable", false)); None } catch { case exception: IllegalArgumentException => Some(exception) }
    oracle.verify(receiverCaught.contains(receiverException) && probe.trace.isEmpty, "Thrown receiver prevents condition evaluation")
    lazy val lazyReceiver: Assertions = { probe.trace += "lazy receiver"; new Assertions {} }
    probe.trace.clear()
    oracle.expectFailure(lazyReceiver.assert(probe.bool("condition", false)))
    oracle.verify(probe.trace.toVector == Vector("lazy receiver", "condition"), "Lazy receiver initializes before condition evaluation")
    object ModuleReceiver extends Assertions { probe.trace += "module receiver" }
    probe.trace.clear()
    oracle.expectFailure(ModuleReceiver.assert(probe.bool("condition", false)))
    oracle.verify(probe.trace.toVector == Vector("module receiver", "condition"), "Module receiver initializes before condition evaluation")
    final class Holder(val receiver: Assertions)
    val absent: Holder = null
    probe.trace.clear()
    // Null-dereference exception semantics depend on the target backend.
    val fieldFailure = try { val _ = absent.receiver; None } catch { case failure: Throwable => Some(failure) }
    if (fieldFailure.isEmpty) throw new IllegalStateException("Direct null field access must fail in this fixture runtime")
    val nullReceiver = try { absent.receiver.assert(probe.bool("unreachable", false)); None } catch { case failure: Throwable => Some(failure) }
    oracle.verify(nullReceiver.map(_.getClass) == fieldFailure.map(_.getClass) && probe.trace.isEmpty, "Stable receiver field access throws before condition evaluation")
    probe.trace.clear()
    val and = oracle.failure(Assert.assert(probe.bool("left", false) && probe.bool("right", true)))
    oracle.verify(probe.trace.toVector == Vector("left"), "Conjunction short circuits")
    oracle.verify(and.diagnostic.observations.exists(_.evaluation == Evaluation.NotEvaluated), "Skipped branch is explicit")
    probe.trace.clear()
    oracle.expectFailure(Assert.assert((probe.bool("left", true) || probe.bool("skipped", false)) && probe.bool("last", false)))
    oracle.verify(probe.trace.toVector == Vector("left", "last"), "Disjunction short circuits in a conjunction")
    probe.trace.clear()
    oracle.expectFailure(Assert.assert(probe.bool("left", false) || probe.bool("right", false)))
    oracle.verify(probe.trace.toVector == Vector("left", "right"), "Disjunction preserves order")
    probe.trace.clear()
    val not = oracle.failure(Assert.assert(!probe.bool("negated", true)))
    oracle.verify(probe.trace.toVector == Vector("negated"), "Negation evaluates once")
    oracle.verify(not.diagnostic.observations.exists(_.site.kind == ObservationKind.BooleanOperator), "Resolved negation is recognized")
    probe.trace.clear()
    val comparison = oracle.failure(Assert.assert(probe.number("left", 1) > probe.number("right", 2)))
    oracle.verify(probe.trace.toVector == Vector("left", "right"), "Comparison operands evaluate once in order")
    oracle.verify(comparison.diagnostic.observations.count(_.site.kind == ObservationKind.Operand) == 2, "Builtin comparison captures operands")

    var comparisons = 0
    final class Ordered {
      def >(other: => Ordered): Boolean = { comparisons += 1; { val _ = other }; { val _ = other }; false }
    }
    var rightEvaluations = 0
    def other: Ordered = { rightEvaluations += 1; new Ordered }
    val opaqueComparison = oracle.failure(Assert.assert(new Ordered > other))
    oracle.verify(comparisons == 1 && rightEvaluations == 2, "Overloaded by-name comparison is invoked once without changing its argument")
    oracle.verify(opaqueComparison.diagnostic.observations.size == 1 && opaqueComparison.diagnostic.observations.head.site.kind == ObservationKind.Opaque, "Overloaded comparison remains opaque")

    var byName = 0
    def twice(condition: => Boolean): Boolean = { val _ = condition; condition }
    oracle.expectFailure(Assert.assert(twice { byName += 1; false }))
    oracle.verify(byName == 2, "Opaque by-name call preserves evaluation count")
    final class BooleanLike {
      def &&(condition: => Boolean): Boolean = { val _ = condition; condition }
    }
    byName = 0
    oracle.expectFailure(Assert.assert(new BooleanLike && { byName += 1; false }))
    oracle.verify(byName == 2, "Operator spelling does not replace a resolved custom method")

    var equalsCalls = 0
    final class Equality {
      override def equals(other: Any): Boolean = { equalsCalls += 1; false }
      override def hashCode(): Int = 0
    }
    oracle.expectFailure(Assert.assert(new Equality == new Equality))
    oracle.verify(equalsCalls == 1, "Universal equality invokes an override once")
    val arrayLeft = Array(1, 2)
    val arrayRight = Array(1, 2)
    oracle.expectFailure(Assert.assert(arrayLeft == arrayRight))
    Assert.assert(arrayLeft eq arrayLeft)
    oracle.expectFailure(Assert.assert(arrayLeft != arrayLeft))
    oracle.verify(arrayLeft.sameElements(arrayRight), "Array equality remains reference equality despite equal contents")

    val thrown = new IllegalArgumentException("operand failure")
    probe.trace.clear()
    def throwing: Int = { probe.trace += "throw"; throw thrown }
    val caught = try { Assert.assert(throwing > probe.number("unreachable", 1)); None } catch { case exception: IllegalArgumentException => Some(exception) }
    oracle.verify(caught.contains(thrown) && probe.trace.toVector == Vector("throw"), "Thrown operand remains the original exception and stops evaluation")
    def generic[A](left: A, right: A): Unit = Assert.assert(left == right)
    oracle.expectFailure(generic(List(1), List(2)))
    generic(List(1), List(1))
    var conditionEvaluations = 0
    var contextEvaluations = 0
    val argumentOrder = ArrayBuffer.empty[String]
    def condition: Boolean = { argumentOrder += "condition"; conditionEvaluations += 1; false }
    def context: AssertionContext = { argumentOrder += "context"; contextEvaluations += 1; AssertionContext.standard }
    oracle.expectFailure(Assert.assert(condition, context))
    oracle.verify(conditionEvaluations == 1 && contextEvaluations == 1, "Explicit context and condition each evaluate once")
    oracle.verify(argumentOrder.toVector == Vector("condition", "context"), "Condition is evaluated before its explicit context")
    def throwingCondition: Boolean = throw thrown
    val caughtCondition = try { Assert.assert(throwingCondition, context); None } catch { case exception: IllegalArgumentException => Some(exception) }
    oracle.verify(caughtCondition.contains(thrown) && contextEvaluations == 1, "Thrown condition prevents context evaluation")
  }

  private def diagnostics(oracle: Oracle, mode: String): String = {
    val minimum = 18
    val age = 17
    val enabled = true
    val failure = oracle.failure(Assert.assert(
      age >= minimum &&
        enabled
    ))
    val source = failure.diagnostic.source
    oracle.verify(source.identity.path.endsWith("AssertionFixtures.scala"), "Compiled source identity is retained")
    val inlineFailure = oracle.failure(Assert.assert(AssertionInlineFixture.condition))
    oracle.verify(inlineFailure.diagnostic.observations.size == 1 && inlineFailure.diagnostic.observations.head.site.kind == ObservationKind.Opaque, "Inline calls retain an opaque observation at the calling expression")
    (inlineFailure.diagnostic.source.span, inlineFailure.diagnostic.source.text) match {
      case (SourceSpan.Range(start, end), CompiledText.Available(text)) =>
        oracle.verify(inlineFailure.diagnostic.observations.forall { observation =>
          (observation.site.span, observation.site.text) match {
            case (SourceSpan.Range(siteStart, siteEnd), CompiledText.Available(siteText)) =>
              siteStart.offset >= start.offset && siteEnd.offset <= end.offset && text.substring(siteStart.offset - start.offset, siteEnd.offset - start.offset) == siteText
            case _ => false
          }
        }, "Every inline observation belongs to the caller's recorded source range")
      case _ => ()
    }
    val sourceDirectory = source.identity.path.stripSuffix("AssertionFixtures.scala")
    val rooted = oracle.failure(Assert.assert(age > minimum, AssertionContext(SourceRoot.Directory(sourceDirectory), SourceProvider.unavailable, ValueRenderer.standard, RenderLimits.standard)))
    oracle.verify(rooted.diagnostic.source.identity == SourceIdentity.Relative("AssertionFixtures.scala"), "Macro source identity uses the supplied root")
    val hasRange = source.span.isInstanceOf[SourceSpan.Range]
    if (mode == "range") oracle.verify(hasRange, "Range-enabled compiler supplies a range")
    else oracle.verify(!hasRange, "Untrusted synthesized ranges are not presented as exact expression spans")
    if (hasRange) {
      oracle.verify(source.span.isInstanceOf[SourceSpan.Range], "Compiler range is preserved")
      oracle.verify(source.text == CompiledText.Available("age >= minimum &&\n        enabled"), s"Multiline compiled text is exact: $source")
      val range = source.span.asInstanceOf[SourceSpan.Range]
      oracle.verify(range.end.offset - range.start.offset == "age >= minimum &&\n        enabled".length, "Offsets use UTF-16 with an exclusive end")
      val prefix = " " * range.start.offset
      val matching = new SourceProvider {
        override def read(identity: SourceIdentity): ProvidedSource = ProvidedSource.Content(prefix + "age >= minimum &&\n        enabled")
      }
      val matchingContext = AssertionContext(SourceRoot.Unspecified, matching, ValueRenderer.standard, RenderLimits.standard)
      oracle.verify(AssertionRenderer.render(failure.diagnostic, matchingContext).sourceValidation == SourceValidation.Matching, "Provider content is validated against recorded span")
    } else {
      oracle.verify(!source.span.isInstanceOf[SourceSpan.Range], "Missing ranges are explicit")
      oracle.verify(failure.getMessage.contains("range unavailable") || failure.getMessage.contains("position unavailable"), "Missing range is visible in the message")
    }
    oracle.verify(failure.getMessage.contains("Assertion failed") && failure.getMessage.contains("source unavailable"), "Unrelated runners receive a useful message without source files")
    val mismatching = new SourceProvider {
      override def read(identity: SourceIdentity): ProvidedSource = ProvidedSource.Content("moved and changed source")
    }
    val mismatched = AssertionRenderer.render(failure.diagnostic, AssertionContext(SourceRoot.Unspecified, mismatching, ValueRenderer.standard, RenderLimits.standard))
    if (hasRange) {
      oracle.verify(mismatched.sourceValidation == SourceValidation.Mismatch && mismatched.text.contains("source mismatch") && mismatched.text.contains("age >= minimum"), "Changed source preserves the compiled excerpt and reports mismatch")
    } else oracle.verify(mismatched.sourceValidation == SourceValidation.RangeUnavailable, "No pointers are inferred from missing ranges")
    val path = SourceIdentity.recorded("/old/project/src/../src/Suite.scala", virtual = false, SourceRoot.Directory("/old/project"))
    oracle.verify(path == SourceIdentity.Relative("src/Suite.scala"), "Source root yields a normalized relative path")
    oracle.verify(SourceIdentity.recorded("C:\\root\\src\\Suite.scala", virtual = false, SourceRoot.Directory("C:\\root")) == SourceIdentity.Relative("src/Suite.scala"), "Windows source paths normalize without filesystem access")
    oracle.verify(SourceIdentity.recorded("<repl-1>", virtual = true, SourceRoot.Unspecified) == SourceIdentity.Virtual("<repl-1>"), "Virtual paths are explicit")
    oracle.verify(SourceIdentity.recorded("C:/../Suite.scala", virtual = false, SourceRoot.Unspecified) == SourceIdentity.Absolute("C:/Suite.scala"), "Parent traversal does not remove a Windows drive root")
    oracle.verify(SourceIdentity.recorded("/../../Suite.scala", virtual = false, SourceRoot.Unspecified) == SourceIdentity.Absolute("/Suite.scala"), "Parent traversal cannot escape a POSIX root")
    oracle.verify(SourceIdentity.recorded("/project/Suite.scala", virtual = false, SourceRoot.Directory(".")) == SourceIdentity.Absolute("/project/Suite.scala"), "Relative root does not change an absolute source identity")
    oracle.verify(SourceIdentity.recorded("\\\\server\\share\\..\\Suite.scala", virtual = false, SourceRoot.Unspecified) == SourceIdentity.Absolute("//server/share/Suite.scala"), "UNC parent traversal preserves its server and share root")
    oracle.verify(SourceIdentity.recorded("../src/../Suite.scala", virtual = false, SourceRoot.Unspecified) == SourceIdentity.Relative("../Suite.scala"), "Relative parent traversal retains unresolved ancestors")
    oracle.verify(SourceIdentity.recorded("C:/project/Suite.scala", virtual = false, SourceRoot.Directory("C:")) == SourceIdentity.Absolute("C:/project/Suite.scala"), "Drive-relative source root does not remove an absolute drive")
    oracle.verify(SourceIdentity.recorded("C:/project/Suite.scala", virtual = false, SourceRoot.Directory("C:.")) == SourceIdentity.Absolute("C:/project/Suite.scala"), "Normalized drive-relative root retains its relative kind")
    oracle.verify(SourceIdentity.recorded("1:/Suite.scala", virtual = false, SourceRoot.Unspecified) == SourceIdentity.Relative("1:/Suite.scala"), "Drive prefixes require an alphabetic drive letter")
    if (hasRange) "range" else "point"
  }

  private def rendering(oracle: Oracle): Unit = {
    var renderedValues = 0
    val renderer = new ValueRenderer {
      override def render[A](value: A): String = { renderedValues += 1; "value" }
    }
    val context = AssertionContext(SourceRoot.Unspecified, SourceProvider.unavailable, renderer, RenderLimits.standard)
    val one = 1
    Assert.assert(one == one, context)
    oracle.verify(renderedValues == 0, "Successful assertions never render values")
    val failure = oracle.failure(Assert.assert(one == 2, context))
    oracle.verify(renderedValues == 0, "Failure construction does not render values")
    val first = failure.getMessage
    val count = renderedValues
    oracle.verify(first.nonEmpty && count > 0, "First message access renders captured values")
    oracle.verify(failure.getMessage == first && renderedValues == count, "Repeated message access reuses its rendered diagnostic")
    val rendererException = new IllegalStateException("renderer failure")
    val failingRenderer = new ValueRenderer {
      override def render[A](value: A): String = throw rendererException
    }
    val renderingFailure = oracle.failure(Assert.assert(one == 2, AssertionContext(SourceRoot.Unspecified, SourceProvider.unavailable, failingRenderer, RenderLimits.standard)))
    oracle.verify(renderingFailure.rendered.renderingFailures.nonEmpty && renderingFailure.rendered.renderingFailures.forall(_.cause eq rendererException), "Renderer errors remain structured on the original assertion failure")
    oracle.verify(renderingFailure.getMessage.contains("value renderer failed") && renderingFailure.diagnostic.observations.nonEmpty, "Renderer errors do not replace original diagnostics")
    val limits = RenderLimits(8, 16, 2, 128, 4)
    val largeRenderer = new ValueRenderer { override def render[A](value: A): String = "x" * 10000 }
    val bounded = oracle.failure(Assert.assert(one == 2, AssertionContext(SourceRoot.Unspecified, SourceProvider.unavailable, largeRenderer, limits)))
    oracle.verify(bounded.getMessage.length <= limits.totalCharacters, "Total output is bounded")
    val text = "\t🐒 != 🐒"
    val source = ExpressionSource(SourceIdentity.Virtual("<unicode>"), SourceSpan.Range(SourcePoint(0, 0, 0), SourcePoint(text.length, 0, text.length)), CompiledText.Available(text))
    val rendered = AssertionRenderer.render(AssertionDiagnostic(source, Vector.empty), AssertionContext.standard)
    oracle.verify(rendered.text.contains("    🐒 != 🐒\n" + "^" * 10), "Tabs expand and surrogate pairs count once in pointers")
    val maximalTabLimits = RenderLimits(8, 16, 1, 128, Int.MaxValue)
    val maximalTabContext = AssertionContext(SourceRoot.Unspecified, SourceProvider.unavailable, ValueRenderer.standard, maximalTabLimits)
    val maximalTabMessage = new AssertionFailure(AssertionDiagnostic(source, Vector.empty), maximalTabContext).getMessage
    oracle.verify(maximalTabMessage.length <= maximalTabLimits.totalCharacters, "Tab expansion is bounded before allocating its output")
    val emojiText = "🐒abc"
    val emojiSource = source.copy(span = SourceSpan.Range(SourcePoint(0, 0, 0), SourcePoint(emojiText.length, 0, emojiText.length)), text = CompiledText.Available(emojiText))
    val shortExcerptContext = AssertionContext(SourceRoot.Unspecified, SourceProvider.unavailable, ValueRenderer.standard, RenderLimits(8, 2, 1, 128, 4))
    val shortExcerpt = AssertionRenderer.render(AssertionDiagnostic(emojiSource, Vector.empty), shortExcerptContext)
    oracle.verify(validUnicode(shortExcerpt.text), "Excerpt truncation does not split a valid surrogate pair")
    val longValue = "x" * 254 + "🐒" + "y"
    val truncatedValue = oracle.failure(Assert.assert(longValue == "different"))
    oracle.verify(validUnicode(truncatedValue.getMessage), "Value truncation does not split a valid surrogate pair")
    val headerCharacters = (emojiSource.identity.path + ":1:1\nAssertion failed\n").length
    val shortTotalContext = shortExcerptContext.copy(limits = RenderLimits(8, 16, 1, headerCharacters + 1, 4))
    val shortTotal = AssertionRenderer.render(AssertionDiagnostic(emojiSource, Vector.empty), shortTotalContext)
    oracle.verify(validUnicode(shortTotal.text) && shortTotal.text.length <= shortTotalContext.limits.totalCharacters, "Total-output truncation does not split a valid surrogate pair")
    oracle.verify(shortTotal.text.substring(headerCharacters).takeWhile(_ != '\n').isEmpty, "Insufficient Unicode output budget never skips into a later source position")
    val providerFailure = new SourceProvider { override def read(identity: SourceIdentity): ProvidedSource = throw rendererException }
    val providerRendered = AssertionRenderer.render(AssertionDiagnostic(source, Vector.empty), AssertionContext(SourceRoot.Unspecified, providerFailure, ValueRenderer.standard, RenderLimits.standard))
    oracle.verify(providerRendered.sourceValidation == SourceValidation.ProviderFailure(rendererException), "Provider failures are explicit")
    val pointSource = source.copy(span = SourceSpan.Point(SourcePoint(0, 0, 0)), text = CompiledText.Unavailable)
    val pointRendered = AssertionRenderer.render(AssertionDiagnostic(pointSource, Vector.empty), AssertionContext.standard)
    oracle.verify(pointRendered.text.contains("range unavailable") && pointRendered.text.contains("compiled expression text unavailable"), "Point-only diagnostics state both missing range and text")
    val absentRendered = AssertionRenderer.render(AssertionDiagnostic(pointSource.copy(span = SourceSpan.Unavailable), Vector.empty), AssertionContext.standard)
    oracle.verify(absentRendered.text.contains("source position unavailable") && !absentRendered.text.contains("^"), "Absent positions never infer pointers")
  }

  private def validUnicode(value: String): Boolean = value.indices.forall { index =>
    val character = value.charAt(index)
    if (Character.isHighSurrogate(character)) index + 1 < value.length && Character.isLowSurrogate(value.charAt(index + 1))
    else if (Character.isLowSurrogate(character)) index > 0 && Character.isHighSurrogate(value.charAt(index - 1))
    else true
  }
}

object AssertionFixturesWithoutRanges {
  def main(args: Array[String]): Unit = {
    require(args.isEmpty, "Unexpected fixture arguments")
    AssertionFixtures.run("point")
  }
}
