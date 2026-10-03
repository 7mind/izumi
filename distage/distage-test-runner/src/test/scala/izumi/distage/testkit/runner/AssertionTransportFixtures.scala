package izumi.distage.testkit.runner

import izumi.distage.testkit.protocol.*
import izumi.distage.testkit.runner.spec.AnyWordSpec
import izumi.fundamentals.{assertions => assertion}

import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.{ExecutionContext, Future, Promise}

private[runner] object AssertionTransportFixtures {
  private final val ValueCharacters = 5
  private final val ObservationLimit = 2
  private final val TotalCharacters = 64

  private final case class SourceCase(
    name: String,
    source: assertion.ExpressionSource,
    provided: () => assertion.ProvidedSource,
    expected: DiagnosticSource,
    validation: DiagnosticSourceValidation,
  )

  private final class Counters {
    val reads = new AtomicInteger(0)
    val renders = new AtomicInteger(0)
    def provider(provided: () => assertion.ProvidedSource): assertion.SourceProvider = new assertion.SourceProvider {
      override def read(identity: assertion.SourceIdentity): assertion.ProvidedSource = { val _ = reads.incrementAndGet(); provided() }
    }
    def renderer(renderValue: () => String): assertion.ValueRenderer = new assertion.ValueRenderer {
      override def render[A](value: A): String = { val _ = renders.incrementAndGet(); renderValue() }
    }
  }

  private final class RecordingSink extends EventSink {
    private var recorded = Vector.empty[ProtocolMessage.Event]
    override def accept(event: ProtocolMessage.Event): Unit = synchronized(recorded :+= event)
    def events: Vector[ProtocolMessage.Event] = synchronized(recorded)
  }

  def run(context: ExecutionContext, verify: (Boolean, String) => Unit): Future[Unit] = {
    implicit val ec: ExecutionContext = context
    macroFailure(context, verify)
      .flatMap {
        _ =>
          val expression = "false &&\n\ttrue😀"
          val identity = assertion.SourceIdentity.Relative("src/Unicode-😀.scala")
          val span = assertion.SourceSpan.Range(assertion.SourcePoint(6, 1, 1), assertion.SourcePoint(22, 2, 7))
          val source = assertion.ExpressionSource(identity, span, assertion.CompiledText.Available(expression))
          val wire = DiagnosticSource(
            DiagnosticSourceIdentity.Relative(identity.path),
            DiagnosticSpan.Range(DiagnosticPoint(6, 1, 1), DiagnosticPoint(22, 2, 7)),
            Some(expression),
          )
          val matching = () => assertion.ProvidedSource.Content("//😀\n\t" + expression + "\n")
          val point = assertion.SourceSpan.Point(assertion.SourcePoint(6, 1, 1))
          val providerError = new IllegalStateException("source provider failed\n😀")
          val cases = Vector(
            SourceCase("matching", source, matching, wire, DiagnosticSourceValidation.Matching),
            SourceCase("mismatch", source, () => assertion.ProvidedSource.Content("changed source"), wire, DiagnosticSourceValidation.Mismatch),
            SourceCase("missing-source", source, () => assertion.ProvidedSource.Unavailable, wire, DiagnosticSourceValidation.Unavailable),
            SourceCase(
              "missing-range",
              source.copy(span = point),
              matching,
              wire.copy(span = DiagnosticSpan.Point(DiagnosticPoint(6, 1, 1))),
              DiagnosticSourceValidation.RangeUnavailable,
            ),
            SourceCase(
              "missing-position-and-text",
              source.copy(span = assertion.SourceSpan.Unavailable, text = assertion.CompiledText.Unavailable),
              matching,
              wire.copy(span = DiagnosticSpan.Unavailable, expression = None),
              DiagnosticSourceValidation.RangeUnavailable,
            ),
            SourceCase(
              "virtual",
              source.copy(identity = assertion.SourceIdentity.Virtual("repl://Unicode-😀")),
              () => assertion.ProvidedSource.Unavailable,
              wire.copy(identity = DiagnosticSourceIdentity.Virtual("repl://Unicode-😀")),
              DiagnosticSourceValidation.Unavailable,
            ),
            SourceCase(
              "provider-failure",
              source,
              () => throw providerError,
              wire,
              DiagnosticSourceValidation.ProviderFailed(providerError.getClass.getName, DiagnosticErrorMessage.Available(providerError.getMessage)),
            ),
          )
          cases.foldLeft(Future.successful(())) {
            (before, sample) =>
              before.flatMap {
                _ =>
                  val counters = new Counters
                  val observations = Vector(
                    assertion.Observation(
                      assertion.ObservationSite(span, assertion.CompiledText.Available("false"), assertion.ObservationKind.BooleanLeaf),
                      assertion.Evaluation.Evaluated(assertion.CapturedValue(false)),
                    ),
                    assertion.Observation(
                      assertion.ObservationSite(point, assertion.CompiledText.Unavailable, assertion.ObservationKind.Comparison),
                      assertion.Evaluation.Evaluated(assertion.CapturedValue(false)),
                    ),
                    assertion.Observation(
                      assertion.ObservationSite(assertion.SourceSpan.Unavailable, assertion.CompiledText.Available("true😀"), assertion.ObservationKind.Opaque),
                      assertion.Evaluation.NotEvaluated,
                    ),
                  )
                  val assertionContext = assertion.AssertionContext(
                    assertion.SourceRoot.Unspecified,
                    counters.provider(sample.provided),
                    counters.renderer(() => "false"),
                    assertion.RenderLimits.standard,
                  )
                  val original = new assertion.AssertionFailure(assertion.AssertionDiagnostic(sample.source, observations), assertionContext)
                  verify(counters.reads.get() == 0 && counters.renders.get() == 0, sample.name + " assertion construction must leave rendering lazy")
                  val expected = AssertionDiagnostic(
                    sample.expected,
                    sample.validation,
                    Vector(
                      DiagnosticObservation(Some("false"), wire.span, DiagnosticObservationKind.BooleanLeaf, ObservedValue.Evaluated("false")),
                      DiagnosticObservation(None, DiagnosticSpan.Point(DiagnosticPoint(6, 1, 1)), DiagnosticObservationKind.Comparison, ObservedValue.Evaluated("false")),
                      DiagnosticObservation(Some("true😀"), DiagnosticSpan.Unavailable, DiagnosticObservationKind.Opaque, ObservedValue.NotEvaluated),
                    ),
                    0,
                  )
                  transport(sample.name, () => throw new RuntimeException("nested assertion", original), context, verify).map {
                    failure =>
                      verify(
                        failure.assertion.contains(expected),
                        sample.name + " source, spans, compiled text, observation kinds/values and validation must survive transport",
                      )
                      verify(counters.reads.get() == 1 && counters.renders.get() == 2, sample.name + " rendering must read once and render only evaluated observations")
                      verify(
                        RunnerFailure.fromThrowable(FailurePhase.Test, original).assertion.contains(expected) && counters.reads.get() == 1 && counters.renders.get() == 2,
                        sample.name + " repeated conversion must reuse cached rendering",
                      )
                      verify(failure.message == original.getMessage, sample.name + " nested original assertion message must be retained")
                      if (sample.name == "mismatch")
                        verify(
                          failure.message.contains("source mismatch") && failure.message.contains("false &&\n^^^^^^^^\n    true😀\n^^^^^^^^^\n"),
                          "Mismatching source must display the compiled excerpt with explicit tab and Unicode rendering",
                        )
                      if (sample.name == "provider-failure")
                        verify(
                          original.rendered.sourceValidation == assertion.SourceValidation.ProviderFailure(providerError),
                          "Original source-provider error object must remain available",
                        )
                  }
              }
          }
      }.flatMap(_ => renderingFailure(context, verify)).flatMap(_ => accessorFailures(context, verify)).flatMap(_ => bounded(context, verify)).map {
        _ =>
          println("ASSERTION_TRANSPORT_FIXTURES_OK cases=16 cached=true wire=verified")
      }
  }

  private def macroFailure(context: ExecutionContext, verify: (Boolean, String) => Unit): Future[Unit] = {
    implicit val ec: ExecutionContext = context
    val counters = new Counters
    val evaluated = new AtomicInteger(0)
    val skipped = new AtomicInteger(0)
    val original = Promise[assertion.AssertionFailure]()
    val configured = assertion.AssertionContext(
      assertion.SourceRoot.Unspecified,
      counters.provider(() => assertion.ProvidedSource.Unavailable),
      counters.renderer(() => "false"),
      assertion.RenderLimits.standard,
    )
    transport(
      "macro",
      () => {
        def left: Boolean = { val _ = evaluated.incrementAndGet(); false }
        def right: Boolean = { val _ = skipped.incrementAndGet(); true }
        assertion.Assert.assert(true, configured)
        verify(counters.reads.get() == 0 && counters.renders.get() == 0, "Successful assertions must leave rendering lazy")
        try assertion.Assert.assert(left && right, configured)
        catch { case failure: assertion.AssertionFailure => val _ = original.success(failure); throw failure }
      },
      context,
      verify,
    ).flatMap {
      failure =>
        original.future.map {
          captured =>
            val diagnostic = failure.assertion.getOrElse(throw new IllegalStateException("Missing macro diagnostic"))
            verify(evaluated.get() == 1 && skipped.get() == 0, "Transport must preserve actual macro short-circuit evaluation")
            verify(diagnostic.source.identity.path.endsWith("AssertionTransportFixtures.scala"), "Transport must retain the macro's compiled source identity")
            captured.diagnostic.source.span match {
              case assertion.SourceSpan.Range(start, end) =>
                verify(
                  diagnostic.source.span == DiagnosticSpan
                    .Range(DiagnosticPoint(start.offset, start.line, start.column), DiagnosticPoint(end.offset, end.line, end.column)),
                  "Macro expression endpoints must survive transport exactly",
                )
                verify(diagnostic.source.expression.contains("left && right"), "Macro transport must retain the compiled expression")
              case assertion.SourceSpan.Point(point) =>
                verify(
                  diagnostic.source.span == DiagnosticSpan.Point(DiagnosticPoint(point.offset, point.line, point.column)) && diagnostic.source.expression.isEmpty,
                  "Unavailable macro ranges and compiled text must remain explicit",
                )
              case assertion.SourceSpan.Unavailable => throw new IllegalStateException("Fixture macro must record a source position")
            }
            verify(
              diagnostic.sourceValidation == DiagnosticSourceValidation.Unavailable && diagnostic.omittedObservations == 0,
              "Macro transport must report missing surrounding source without losing observations",
            )
            verify(
              diagnostic.observations.count(_.value == ObservedValue.NotEvaluated) == 1 && diagnostic.observations.exists(
                _.kind == DiagnosticObservationKind.BooleanOperator
              ),
              "Macro transport must preserve its skipped observation and resolved operator",
            )
            verify(counters.reads.get() == 1 && counters.renders.get() == 2 && failure.message == captured.getMessage, "Macro rendering must be cached after transport")
        }
    }
  }

  private def renderingFailure(context: ExecutionContext, verify: (Boolean, String) => Unit): Future[Unit] = {
    implicit val ec: ExecutionContext = context
    val counters = new Counters
    val error = new IllegalArgumentException("value renderer failed\n😀")
    val source = assertion.ExpressionSource(
      assertion.SourceIdentity.Virtual("renderer://fixture"),
      assertion.SourceSpan.Unavailable,
      assertion.CompiledText.Available("opaque(value)"),
    )
    val site = assertion.ObservationSite(assertion.SourceSpan.Unavailable, assertion.CompiledText.Unavailable, assertion.ObservationKind.Opaque)
    val observation = assertion.Observation(site, assertion.Evaluation.Evaluated(assertion.CapturedValue(false)))
    val configured = assertion.AssertionContext(
      assertion.SourceRoot.Unspecified,
      counters.provider(() => assertion.ProvidedSource.Unavailable),
      counters.renderer(() => throw error),
      assertion.RenderLimits.standard,
    )
    val original = new assertion.AssertionFailure(assertion.AssertionDiagnostic(source, Vector(observation)), configured)
    transport("rendering-failure", () => throw original, context, verify).map {
      failure =>
        val expected = AssertionDiagnostic(
          DiagnosticSource(DiagnosticSourceIdentity.Virtual("renderer://fixture"), DiagnosticSpan.Unavailable, Some("opaque(value)")),
          DiagnosticSourceValidation.Unavailable,
          Vector(
            DiagnosticObservation(
              None,
              DiagnosticSpan.Unavailable,
              DiagnosticObservationKind.Opaque,
              ObservedValue.RenderingFailed(error.getClass.getName, DiagnosticErrorMessage.Available(error.getMessage)),
            )
          ),
          0,
        )
        verify(
          failure.assertion.contains(expected) && failure.exceptionClass == original.getClass.getName,
          "Renderer failure must retain the original assertion and expose the rendering error",
        )
        verify(
          original.rendered.renderingFailures.size == 1 && (original.rendered.renderingFailures.head.cause eq error),
          "Renderer failure must preserve the exact original rendering error",
        )
        verify(
          RunnerFailure.fromThrowable(FailurePhase.Test, original).assertion.contains(expected) && counters.renders.get() == 1 && counters.reads.get() == 1,
          "Renderer failure must remain cached during repeated conversion",
        )
    }
  }

  private def accessorFailures(context: ExecutionContext, verify: (Boolean, String) => Unit): Future[Unit] = {
    implicit val ec: ExecutionContext = context
    Vector("renderer", "provider", "renderer-no-message", "provider-no-message", "renderer-changing-message", "provider-changing-message").foldLeft(
      Future.successful(())
    ) {
      (before, mode) =>
        before.flatMap {
          _ =>
            val counters = new Counters
            val messages = new AtomicInteger(0)
            val accessorError = new IllegalStateException("message accessor failed")
            val error = new RuntimeException("inaccessible") {
              override def getMessage: String = {
                val count = messages.incrementAndGet()
                if (mode.endsWith("-changing-message")) "message-" + count
                else if (mode.endsWith("-no-message")) null
                else throw accessorError
              }
            }
            val source = assertion.ExpressionSource(
              assertion.SourceIdentity.Virtual("accessor://" + mode),
              assertion.SourceSpan.Unavailable,
              assertion.CompiledText.Available("false"),
            )
            val site = assertion.ObservationSite(assertion.SourceSpan.Unavailable, assertion.CompiledText.Available("false"), assertion.ObservationKind.BooleanLeaf)
            val observation = assertion.Observation(site, assertion.Evaluation.Evaluated(assertion.CapturedValue(false)))
            val isProvider = mode.startsWith("provider")
            val provider = counters.provider(() => if (isProvider) throw error else assertion.ProvidedSource.Unavailable)
            val renderer = counters.renderer(() => if (isProvider) "false" else throw error)
            val original = new assertion.AssertionFailure(
              assertion.AssertionDiagnostic(source, Vector(observation)),
              assertion.AssertionContext(assertion.SourceRoot.Unspecified, provider, renderer, assertion.RenderLimits.standard),
            )
            val message =
              if (mode.endsWith("-changing-message")) DiagnosticErrorMessage.Available("message-1")
              else if (mode.endsWith("-no-message")) DiagnosticErrorMessage.Unavailable
              else DiagnosticErrorMessage.AccessorFailed(accessorError.getClass.getName)
            val validation = if (isProvider) DiagnosticSourceValidation.ProviderFailed(error.getClass.getName, message) else DiagnosticSourceValidation.Unavailable
            val value = if (isProvider) ObservedValue.Evaluated("false") else ObservedValue.RenderingFailed(error.getClass.getName, message)
            val expected = AssertionDiagnostic(
              DiagnosticSource(DiagnosticSourceIdentity.Virtual("accessor://" + mode), DiagnosticSpan.Unavailable, Some("false")),
              validation,
              Vector(DiagnosticObservation(Some("false"), DiagnosticSpan.Unavailable, DiagnosticObservationKind.BooleanLeaf, value)),
              0,
            )
            transport(mode, () => throw original, context, verify).map {
              failure =>
                verify(
                  failure.assertion.contains(expected) && failure.message == original.getMessage,
                  mode + " message accessor must not replace the original assertion or hide the accessor failure",
                )
                verify(
                  RunnerFailure.fromThrowable(FailurePhase.Test, original).assertion.contains(expected) && counters.reads.get() == 1 && counters.renders
                    .get() == 1 && messages.get() == 1,
                  mode + " accessor failure must preserve one message snapshot without repeating rendering or source access",
                )
                val retained =
                  if (isProvider) original.rendered.sourceValidation == assertion.SourceValidation.ProviderFailure(error)
                  else original.rendered.renderingFailures.exists(_.cause eq error)
                verify(retained, mode + " diagnostic must retain its exact original embedded exception")
            }
        }
    }
  }

  private def bounded(context: ExecutionContext, verify: (Boolean, String) => Unit): Future[Unit] = {
    implicit val ec: ExecutionContext = context
    val counters = new Counters
    val source = assertion.ExpressionSource(
      assertion.SourceIdentity.Absolute("/src/Bounds.scala"),
      assertion.SourceSpan.Point(assertion.SourcePoint(0, 0, 0)),
      assertion.CompiledText.Available("compiled expression"),
    )
    val site = assertion.ObservationSite(assertion.SourceSpan.Unavailable, assertion.CompiledText.Available("operand"), assertion.ObservationKind.Operand)
    val observations = Vector.fill(4)(assertion.Observation(site, assertion.Evaluation.Evaluated(assertion.CapturedValue("ab😀cd"))))
    val limits = assertion.RenderLimits(ValueCharacters, 16, ObservationLimit, TotalCharacters, 4)
    val configured = assertion.AssertionContext(
      assertion.SourceRoot.Unspecified,
      counters.provider(() => assertion.ProvidedSource.Unavailable),
      counters.renderer(() => "ab😀cd"),
      limits,
    )
    val original = new assertion.AssertionFailure(assertion.AssertionDiagnostic(source, observations), configured)
    transport("bounded", () => throw original, context, verify).map {
      failure =>
        val diagnostic = failure.assertion.getOrElse(throw new IllegalStateException("Missing bounded diagnostic"))
        verify(
          diagnostic.source.identity == DiagnosticSourceIdentity.Absolute("/src/Bounds.scala") && diagnostic.source.expression.contains("compiled expression"),
          "Transport must retain absolute source identity and complete compiled text independently of message limits",
        )
        verify(
          diagnostic.observations.size == ObservationLimit && diagnostic.omittedObservations == 2 && diagnostic.observations.forall(
            _.value == ObservedValue.Evaluated("ab😀…")
          ),
          "Transport must preserve bounded values without splitting Unicode and explicitly count omitted observations",
        )
        verify(
          failure.message.length <= TotalCharacters && original.rendered.values.size == ObservationLimit && counters.renders.get() == ObservationLimit,
          "Message and rendered observations must obey their limits without evaluating omitted values",
        )
    }
  }

  private def transport(name: String, body: () => Unit, context: ExecutionContext, verify: (Boolean, String) => Unit): Future[Failure] = {
    implicit val ec: ExecutionContext = context
    final class Suite extends AnyWordSpec { "assertion transport" should { name in { body() } } }
    val identity = CatalogueIdentity(BuildId("assertion-transport"), BuildTargetId("portable"), CatalogueId(name))
    val sink = new RecordingSink
    val session = new RunSession(identity, Vector(() => new Suite), context, sink)
    session.execute(RunId(name), RunRequest(identity, Selection.All, RunOverrides(Vector.empty, Vector.empty, MemoizationOverride.Inherit))).map {
      outcome =>
        verify(
          !outcome.successful && outcome.results.size == 1 && outcome.results.head.status == TestStatus.Failed,
          name + " public runner must report its assertion failure",
        )
        def assertions(failure: Failure): Vector[Failure] = {
          val own = if (failure.exceptionClass.endsWith("AssertionFailure")) Vector(failure) else Vector.empty
          own ++ failure.causes.flatMap(assertions)
        }
        val failures = outcome.results.head.failure.toVector.flatMap(assertions)
        verify(failures.size == 1 && failures.head.assertion.nonEmpty, name + " public runner must retain exactly one structured assertion")
        val completed = ProtocolMessage.Completed(outcome)
        verify(ProtocolCodec.decode(ProtocolCodec.encode(completed)) == Right(completed), name + " complete failure tree and diagnostic must round-trip")
        verify(
          sink.events.nonEmpty && sink.events.last.event == RunEvent.Finished(outcome.run, outcome) && sink.events
            .forall(event => ProtocolCodec.decode(ProtocolCodec.encode(event)) == Right(event)),
          name + " structured event diagnostics must round-trip through terminal completion",
        )
        failures.head
    }
  }
}
