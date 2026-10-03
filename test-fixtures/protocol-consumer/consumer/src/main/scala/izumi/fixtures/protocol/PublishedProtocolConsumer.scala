package izumi.fixtures.protocol

import izumi.distage.testkit.protocol.*

object PublishedProtocolConsumer {
  def roundTrip(frame: String): String = ProtocolCodec.decode(frame) match {
    case Right(message) => ProtocolCodec.encode(message)
    case Left(error) => throw new IllegalArgumentException(error.message)
  }

  def diagnosticFrame: String = {
    val source = DiagnosticSource(
      DiagnosticSourceIdentity.Virtual("consumer://source-😀"),
      DiagnosticSpan.Range(DiagnosticPoint(2, 0, 2), DiagnosticPoint(15, 1, 4)),
      Some("left &&\nright"),
    )
    val diagnostic = AssertionDiagnostic(
      source,
      DiagnosticSourceValidation.Mismatch,
      Vector(
        DiagnosticObservation(Some("left"), DiagnosticSpan.Point(DiagnosticPoint(2, 0, 2)), DiagnosticObservationKind.BooleanLeaf, ObservedValue.Evaluated("false")),
        DiagnosticObservation(Some("right"), DiagnosticSpan.Unavailable, DiagnosticObservationKind.BooleanOperator, ObservedValue.NotEvaluated),
        DiagnosticObservation(None, source.span, DiagnosticObservationKind.Opaque, ObservedValue.RenderingFailed("RendererFailure", DiagnosticErrorMessage.Available("failed\n😀"))),
      ),
      2,
    )
    ProtocolCodec.encode(
      ProtocolMessage.Rejected(RunId("diagnostic"), Failure(FailurePhase.Test, "AssertionFailure", "consumer failure", Vector.empty, Vector.empty, Some(diagnostic)))
    )
  }

  def main(args: Array[String]): Unit = {
    val golden = "{\"schemaVersion\":2,\"message\":{\"kind\":\"cancel\",\"run\":\"published-consumer\"}}"
    if (roundTrip(golden) != golden || ProtocolCodec.decode(golden) != Right(ProtocolMessage.Cancel(RunId("published-consumer")))) {
      throw new IllegalStateException("Published protocol changed the common wire schema")
    }
    if (roundTrip(diagnosticFrame) != diagnosticFrame)
      throw new IllegalStateException("Published protocol lost structured assertion source, spans, validation or observations")
    if (!ProtocolCodec.decode(golden.replace("\"schemaVersion\":2", "\"schemaVersion\":1")).left.exists(_.message.contains("Unsupported protocol schema"))) {
      throw new IllegalStateException("Published protocol must reject the previous diagnostic schema")
    }
    val identity = CatalogueIdentity(BuildId("consumer-build"), BuildTargetId("consumer-target"), CatalogueId("consumer-catalogue"))
    val request = ProtocolMessage.Request(RequestOperation.Execute, RunId("request-one"), RunRequest(
      identity,
      Selection.Only(Vector.empty, Vector(TestId(identity.target, SuiteId("ConsumerSuite"), Vector("separate", "path segments"), Some("variant")))),
      RunOverrides(Vector.empty, Vector.empty, MemoizationOverride.Disabled),
    ))
    if (ProtocolCodec.decode(ProtocolCodec.encode(request)) != Right(request)) {
      throw new IllegalStateException("Published protocol lost selection identity or effective settings")
    }
    val failure = Failure(FailurePhase.Test, "AssertionFailure", "consumer failure", Vector.empty, Vector.empty, None)
    val id = TestId(identity.target, SuiteId("ConsumerSuite"), Vector("failure"), None)
    val skippedWithFailure = RunOutcome(RunId("invalid-skip"), Vector(TestResult(id, TestStatus.Skipped, Some(failure), Long.MaxValue)), Vector.empty, cancelled = false)
    if (skippedWithFailure.successful) throw new IllegalStateException("Published aggregate treats a failure as success")
    def rejectProducer(message: ProtocolMessage, reason: String): Unit = {
      val rejected = try {
        val _ = ProtocolCodec.encode(message)
        false
      } catch {
        case error: IllegalArgumentException => error.getMessage.contains(reason)
      }
      if (!rejected) throw new IllegalStateException(s"Published producer must reject: $reason")
    }
    rejectProducer(ProtocolMessage.Completed(skippedWithFailure), "Skipped test must not carry a failure")
    var nested = failure
    var depth = 1
    while (depth < 32) {
      nested = failure.copy(causes = Vector(nested))
      depth += 1
    }
    val deepestSupported = ProtocolMessage.Rejected(RunId("boundary"), nested)
    if (ProtocolCodec.MaxFailureDepth != 32 || ProtocolCodec.decode(ProtocolCodec.encode(deepestSupported)) != Right(deepestSupported)) {
      throw new IllegalStateException("Published failure-depth boundary must round-trip")
    }
    rejectProducer(ProtocolMessage.Rejected(RunId("excess-depth"), failure.copy(causes = Vector(nested))), "Failure cause depth")
    println("PUBLISHED_PROTOCOL_CONSUMER_OK schema=2 boundaries=verified diagnostic=structured")
  }
}
