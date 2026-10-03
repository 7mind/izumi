package izumi.distage.testkit.protocol

import io.circe.{ACursor, HCursor, Json}
import io.circe.parser.parse

object ProtocolFixtures {
  def main(args: Array[String]): Unit = {
    var checks = 0
    def verify(condition: Boolean, message: String): Unit = {
      checks += 1
      if (!condition) throw new IllegalStateException(message)
    }
    val run = RunId("protocol-fixture")
    val target = BuildTargetId("fixtures/jvm")
    val suite = SuiteId("example.WordSpec")
    val identity = CatalogueIdentity(BuildId("build-one"), target, CatalogueId("catalogue-one"))
    val first = TestId(target, suite, Vector("a b", "c"), None)
    val second = TestId(target, suite, Vector("a", "b c"), Some("variant-one"))
    val location = SourceLocation.Known("src/Unicode-😀.scala", 7, Some(9))
    val settings = EffectiveSettings(Vector(AxisChoice(AxisId("repo"), AxisValue("dummy"))), memoization = true)
    val catalogue = Catalogue(identity, Vector(SuiteDescriptor(suite, "WordSpec")), Vector(
      TestDescriptor(first, "a b c", location, settings),
      TestDescriptor(second, "a b c", SourceLocation.Unavailable, settings),
    ))
    val span = DiagnosticSpan.Range(DiagnosticPoint(0, 7, 9), DiagnosticPoint(13, 7, 22))
    val source = DiagnosticSource(DiagnosticSourceIdentity.Relative("src/Unicode-😀.scala"), span, Some("left && right"))
    val diagnostic = AssertionDiagnostic(source, DiagnosticSourceValidation.Mismatch, Vector(
      DiagnosticObservation(Some("left"), span, DiagnosticObservationKind.BooleanLeaf, ObservedValue.Evaluated("false\n\"quoted\"\\😀")),
      DiagnosticObservation(Some("right"), DiagnosticSpan.Unavailable, DiagnosticObservationKind.Operand, ObservedValue.NotEvaluated),
      DiagnosticObservation(None, DiagnosticSpan.Point(DiagnosticPoint(0, 7, 9)), DiagnosticObservationKind.Opaque, ObservedValue.RenderingFailed("RendererFailure", DiagnosticErrorMessage.Available("renderer threw"))),
    ), 2)
    val failure = Failure(FailurePhase.Test, "AssertionFailure", "false\nstdout: {}", Vector("example.WordSpec.in(WordSpec.scala:8)"), Vector.empty, Some(diagnostic))
    val result = TestResult(first, TestStatus.Failed, Some(failure), Long.MaxValue)
    val outcome = RunOutcome(run, Vector(result), Vector(failure.copy(phase = FailurePhase.Finalization)), cancelled = false)
    val overrides = RunOverrides(Vector.empty, Vector.empty, MemoizationOverride.Inherit)
    val request = RunRequest(identity, Selection.Only(Vector(suite), Vector(second)), overrides)
    val messages = Vector[ProtocolMessage](
      ProtocolMessage.Discover(run, identity.build, target),
      ProtocolMessage.Discovered(run, catalogue),
      ProtocolMessage.Request(RequestOperation.Resolve, run, request),
      ProtocolMessage.Request(RequestOperation.Plan, run, request.copy(selection = Selection.All, overrides = overrides.copy(memoization = MemoizationOverride.Disabled))),
      ProtocolMessage.Request(RequestOperation.Execute, run, request.copy(overrides = overrides.copy(memoization = MemoizationOverride.Enabled))),
      ProtocolMessage.Cancel(run),
      ProtocolMessage.Event(0, RunEvent.Started(run)),
      ProtocolMessage.Event(1, RunEvent.TestStarted(run, first)),
      ProtocolMessage.Event(2, RunEvent.TestCompleted(run, result)),
      ProtocolMessage.Event(3, RunEvent.PhaseFailed(run, failure)),
      ProtocolMessage.Event(Long.MaxValue, RunEvent.Finished(run, outcome)),
      ProtocolMessage.Completed(outcome),
      ProtocolMessage.Rejected(run, failure.copy(causes = Vector(failure))),
    )
    messages.foreach { message =>
      val frame = ProtocolCodec.encode(message)
      verify(!frame.contains('\n') && !frame.contains('\r'), "Embedded test output must remain JSON-escaped inside one frame")
      val decoded = ProtocolCodec.decode(frame)
      verify(decoded == Right(message), s"Wire round-trip changed $message into $decoded")
    }
    val golden = "{\"schemaVersion\":2,\"message\":{\"kind\":\"cancel\",\"run\":\"protocol-fixture\"}}"
    verify(ProtocolCodec.encode(ProtocolMessage.Cancel(run)) == golden, "All compiler/platform lanes must emit the same golden frame")
    verify(ProtocolCodec.decode(golden) == Right(ProtocolMessage.Cancel(run)), "Golden frame must decode")
    verify(first != second, "Structured paths and variants distinguish equal display names")
    verify(!outcome.successful, "Body or finalization failures prevent successful outcome")
    verify(!outcome.copy(results = Vector.empty, failures = Vector.empty, cancelled = true).successful, "Cancellation prevents successful outcome")
    verify(outcome.copy(results = Vector(result.copy(status = TestStatus.Succeeded, failure = None)), failures = Vector.empty).successful, "Successful results without finalization failure succeed")
    def reject(frame: String, reason: String): Unit = {
      verify(ProtocolCodec.decode(frame).left.exists(_.message.contains(reason)), s"Expected protocol rejection: $reason")
    }
    reject(golden.replace("\"schemaVersion\":2", "\"schemaVersion\":1"), "Unsupported protocol schema")
    reject(golden.replace("\"schemaVersion\":2", "\"schemaVersion\":3"), "Unsupported protocol schema")
    reject(golden.replace("\"kind\":\"cancel\"", "\"kind\":\"unknown\""), "Unknown protocol kind")
    reject(golden.replace("protocol-fixture", ""), "Identity must not be empty")
    reject(golden + "\n", "one channel line")
    reject("x" * (ProtocolCodec.MaxFrameCharacters + 1), "character limit")
    def corrupt(message: ProtocolMessage)(edit: HCursor => ACursor): String = {
      val json = parse(ProtocolCodec.encode(message)).fold(error => throw new IllegalStateException(error.message), value => value)
      edit(json.hcursor).top.get.noSpaces
    }
    val emptySelection = ProtocolMessage.Request(RequestOperation.Execute, run, request.copy(selection = Selection.Only(Vector.empty, Vector(second))))
    reject(corrupt(emptySelection)(_.downField("message").downField("request").downField("selection").downField("tests").withFocus(_ => Json.arr())), "Explicit selection must not be empty")
    reject(corrupt(ProtocolMessage.Event(0, RunEvent.Started(run)))(_.downField("message").downField("sequence").withFocus(_ => Json.fromString("-1"))), "sequence must not be negative")
    reject(corrupt(ProtocolMessage.Event(0, RunEvent.Finished(run, outcome)))(_.downField("message").downField("event").downField("outcome").downField("run").withFocus(_ => Json.fromString("other"))), "identities differ")
    reject(corrupt(ProtocolMessage.Completed(outcome))(_.downField("message").downField("outcome").downField("results").downArray.downField("failure").withFocus(_ => Json.Null)), "must carry a failure")
    reject(corrupt(ProtocolMessage.Discovered(run, catalogue))(_.downField("message").downField("catalogue").downField("tests").downArray.downField("id").downField("path").withFocus(_ => Json.arr())), "path must not be empty")
    def rejectProducer(message: ProtocolMessage, reason: String): Unit = {
      val rejected = try {
        val _ = ProtocolCodec.encode(message)
        false
      } catch {
        case failure: IllegalArgumentException => failure.getMessage.contains(reason)
      }
      verify(rejected, s"Producer must reject an invalid protocol state: $reason")
    }
    val validations = Vector[DiagnosticSourceValidation](
      DiagnosticSourceValidation.Matching, DiagnosticSourceValidation.Mismatch, DiagnosticSourceValidation.Unavailable,
      DiagnosticSourceValidation.RangeUnavailable, DiagnosticSourceValidation.ProviderFailed("SourceFailure", DiagnosticErrorMessage.Available("missing\n😀")),
      DiagnosticSourceValidation.ProviderFailed("SourceFailure", DiagnosticErrorMessage.Unavailable),
      DiagnosticSourceValidation.ProviderFailed("SourceFailure", DiagnosticErrorMessage.AccessorFailed("AccessorFailure")),
    )
    val identities = Vector[DiagnosticSourceIdentity](source.identity, DiagnosticSourceIdentity.Absolute("/src/Unicode-😀.scala"), DiagnosticSourceIdentity.Virtual("repl://Unicode-😀"))
    val spans = Vector[DiagnosticSpan](span, DiagnosticSpan.Point(DiagnosticPoint(0, 7, 9)), DiagnosticSpan.Unavailable)
    identities.zip(spans).foreach { case (identity, position) => validations.foreach { validation =>
      val changed = diagnostic.copy(source = source.copy(identity = identity, span = position, expression = None), sourceValidation = validation)
      val message = ProtocolMessage.Rejected(run, failure.copy(assertion = Some(changed)))
      verify(ProtocolCodec.decode(ProtocolCodec.encode(message)) == Right(message), "Diagnostic source identity, missing text/span and validation must round-trip")
    } }
    Vector[DiagnosticErrorMessage](DiagnosticErrorMessage.Unavailable, DiagnosticErrorMessage.AccessorFailed("AccessorFailure")).foreach { message =>
      val changed = diagnostic.copy(observations = Vector(diagnostic.observations.head.copy(value = ObservedValue.RenderingFailed("RendererFailure", message))))
      val rejected = ProtocolMessage.Rejected(run, failure.copy(assertion = Some(changed)))
      verify(ProtocolCodec.decode(ProtocolCodec.encode(rejected)) == Right(rejected), "Unavailable and failed error-message accessors must remain explicit")
    }
    def rejectDiagnostic(changed: AssertionDiagnostic, reason: String)(edit: ACursor => ACursor): Unit = {
      val message = ProtocolMessage.Rejected(run, failure.copy(assertion = Some(changed)))
      rejectProducer(message, reason)
      val frame = corrupt(ProtocolMessage.Rejected(run, failure))(cursor => edit(cursor.downField("message").downField("failure").downField("assertion")))
      reject(frame, reason)
    }
    rejectDiagnostic(diagnostic.copy(source = source.copy(identity = DiagnosticSourceIdentity.Relative(""))), "Diagnostic source identity")(_.downField("source").downField("identity").downField("path").withFocus(_ => Json.fromString("")))
    rejectDiagnostic(diagnostic.copy(source = source.copy(span = DiagnosticSpan.Point(DiagnosticPoint(-1, 0, 0)))), "Invalid diagnostic source point")(_.downField("source").downField("span").downField("start").downField("offset").withFocus(_ => Json.fromInt(-1)))
    rejectDiagnostic(diagnostic.copy(source = source.copy(span = DiagnosticSpan.Range(DiagnosticPoint(0, 7, 9), DiagnosticPoint(13, 7, 8)))), "Invalid diagnostic source range")(_.downField("source").downField("span").downField("end").downField("column").withFocus(_ => Json.fromInt(8)))
    rejectDiagnostic(diagnostic.copy(omittedObservations = -1), "Omitted observation count")(_.downField("omittedObservations").withFocus(_ => Json.fromInt(-1)))
    rejectProducer(ProtocolMessage.Request(RequestOperation.Execute, run, request.copy(selection = Selection.Only(Vector.empty, Vector.empty))), "Explicit selection must not be empty")
    rejectProducer(ProtocolMessage.Event(-1, RunEvent.Started(run)), "sequence must not be negative")
    rejectProducer(ProtocolMessage.Event(0, RunEvent.Finished(run, outcome.copy(run = RunId("other")))), "identities differ")
    rejectProducer(ProtocolMessage.Completed(outcome.copy(results = Vector(result.copy(failure = None)))), "must carry a failure")
    rejectProducer(ProtocolMessage.Discovered(run, catalogue.copy(tests = Vector(catalogue.tests.head.copy(id = first.copy(path = Vector.empty))))), "path must not be empty")
    rejectProducer(ProtocolMessage.Completed(outcome.copy(results = Vector(result.copy(status = TestStatus.Succeeded)), failures = Vector.empty)), "Successful test must not carry a failure")
    val skippedWithFailure = outcome.copy(results = Vector(result.copy(status = TestStatus.Skipped)), failures = Vector.empty)
    verify(!skippedWithFailure.successful, "Skipped result carrying a failure must not report aggregate success")
    rejectProducer(ProtocolMessage.Completed(skippedWithFailure), "Skipped test must not carry a failure")
    val depthLimit = 32
    def failureAtDepth(depth: Int): Failure = {
      var nested = failure.copy(causes = Vector.empty, assertion = None)
      var remaining = depth - 1
      while (remaining > 0) {
        nested = failure.copy(causes = Vector(nested), assertion = None)
        remaining -= 1
      }
      nested
    }
    def failureFrameAtDepth(depth: Int): String = {
      val prefix = "{\"phase\":\"test\",\"exceptionClass\":\"Failure\",\"message\":\"failure\",\"stack\":[],\"causes\":["
      val suffix = "],\"assertion\":null}"
      var nested = prefix + suffix
      var remaining = depth - 1
      while (remaining > 0) {
        nested = prefix + nested + suffix
        remaining -= 1
      }
      s"""{"schemaVersion":2,"message":{"kind":"rejected","run":"protocol-fixture","failure":$nested}}"""
    }
    val deepestSupported = ProtocolMessage.Rejected(run, failureAtDepth(depthLimit))
    verify(ProtocolCodec.validate(deepestSupported) == Right(()), "Payload validation must accept its failure nesting boundary")
    verify(ProtocolCodec.validate(ProtocolMessage.Rejected(run, failureAtDepth(depthLimit + 1))).left.exists(_.message.contains("Failure cause depth")), "Payload validation must reject excess failure depth")
    verify(ProtocolCodec.decode(ProtocolCodec.encode(deepestSupported)) == Right(deepestSupported), "Failure nesting boundary must round-trip")
    reject(failureFrameAtDepth(depthLimit + 1), "Failure cause depth")
    rejectProducer(ProtocolMessage.Rejected(run, failureAtDepth(depthLimit + 1)), "Failure cause depth")
    reject(failureFrameAtDepth(512), "JSON nesting")
    rejectProducer(ProtocolMessage.Rejected(run, failureAtDepth(512)), "Failure cause depth")
    verify(ProtocolCodec.MaxFailureDepth == depthLimit, "Published failure nesting policy must match its boundary fixtures")
    val jsonDepthLimit = 128
    def extraNestingFrame(depth: Int): String = {
      val extra = ("[" * (depth - 1)) + "0" + ("]" * (depth - 1))
      golden.dropRight(1) + s""", "extra":$extra}"""
    }
    verify(ProtocolCodec.decode(extraNestingFrame(jsonDepthLimit)) == Right(ProtocolMessage.Cancel(run)), "JSON nesting boundary must decode")
    reject(extraNestingFrame(jsonDepthLimit + 1), "JSON nesting")
    verify(ProtocolCodec.MaxJsonDepth == jsonDepthLimit, "Published JSON nesting policy must match its boundary fixtures")
    val quotedBraces = ProtocolMessage.Cancel(RunId(("\"\\{}[]" * 300) + "😀"))
    verify(ProtocolCodec.decode(ProtocolCodec.encode(quotedBraces)) == Right(quotedBraces), "Escaped quotes, backslashes and quoted braces must not increase JSON nesting")
    val oversizedPayload = ProtocolMessage.Rejected(run, failure.copy(message = "x" * (ProtocolCodec.MaxFrameCharacters + 1)))
    verify(ProtocolCodec.validate(oversizedPayload) == Right(()), "Payload schema validation does not impose channel frame size on in-process values")
    rejectProducer(oversizedPayload, "character limit")
    println(s"PROTOCOL_FIXTURES_OK checks=$checks schema=${ProtocolCodec.SchemaVersion}")
    println(s"PROTOCOL_GOLDEN $golden")
  }
}
