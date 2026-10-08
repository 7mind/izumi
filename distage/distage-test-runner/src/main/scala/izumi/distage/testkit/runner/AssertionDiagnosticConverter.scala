package izumi.distage.testkit.runner

import izumi.distage.testkit.protocol.*
import izumi.fundamentals.{assertions => assertion}

private[runner] object AssertionDiagnosticConverter {
  def convert(failure: assertion.AssertionFailure): AssertionDiagnostic = {
    val diagnostic = failure.diagnostic
    val rendered = failure.rendered
    require(rendered.values.size <= diagnostic.observations.size, "Rendered observations exceed captured observations")
    val observations = diagnostic.observations.take(rendered.values.size).zip(rendered.values).map {
      case (observation, value) =>
        DiagnosticObservation(text(observation.site.text), span(observation.site.span), kind(observation.site.kind), observedValue(value))
    }
    AssertionDiagnostic(
      DiagnosticSource(identity(diagnostic.source.identity), span(diagnostic.source.span), text(diagnostic.source.text)),
      validation(rendered.sourceValidation),
      observations,
      diagnostic.observations.size - observations.size,
    )
  }

  private def identity(value: assertion.SourceIdentity): DiagnosticSourceIdentity = value match {
    case assertion.SourceIdentity.Relative(path) => DiagnosticSourceIdentity.Relative(path)
    case assertion.SourceIdentity.Absolute(path) => DiagnosticSourceIdentity.Absolute(path)
    case assertion.SourceIdentity.Virtual(path) => DiagnosticSourceIdentity.Virtual(path)
  }

  private def point(value: assertion.SourcePoint): DiagnosticPoint = DiagnosticPoint(value.offset, value.line, value.column)

  private def span(value: assertion.SourceSpan): DiagnosticSpan = value match {
    case assertion.SourceSpan.Range(start, end) => DiagnosticSpan.Range(point(start), point(end))
    case assertion.SourceSpan.Point(value) => DiagnosticSpan.Point(point(value))
    case assertion.SourceSpan.Unavailable => DiagnosticSpan.Unavailable
  }

  private def text(value: assertion.CompiledText): Option[String] = value match {
    case assertion.CompiledText.Available(value) => Some(value)
    case assertion.CompiledText.Reconstructed(value) => Some(s"[AST fallback] $value")
    case assertion.CompiledText.Unavailable => None
  }

  private def validation(value: assertion.SourceValidation): DiagnosticSourceValidation = value match {
    case assertion.SourceValidation.Matching => DiagnosticSourceValidation.Matching
    case assertion.SourceValidation.Mismatch => DiagnosticSourceValidation.Mismatch
    case assertion.SourceValidation.Unavailable => DiagnosticSourceValidation.Unavailable
    case assertion.SourceValidation.RangeUnavailable => DiagnosticSourceValidation.RangeUnavailable
    case assertion.SourceValidation.TextUnavailable => DiagnosticSourceValidation.TextUnavailable
    case failure: assertion.SourceValidation.ProviderFailure => DiagnosticSourceValidation.ProviderFailed(failure.cause.getClass.getName, errorMessage(failure.message))
  }

  private def kind(value: assertion.ObservationKind): DiagnosticObservationKind = value match {
    case assertion.ObservationKind.BooleanLeaf => DiagnosticObservationKind.BooleanLeaf
    case assertion.ObservationKind.BooleanOperator => DiagnosticObservationKind.BooleanOperator
    case assertion.ObservationKind.Comparison => DiagnosticObservationKind.Comparison
    case assertion.ObservationKind.Operand => DiagnosticObservationKind.Operand
    case assertion.ObservationKind.Opaque => DiagnosticObservationKind.Opaque
  }

  private def observedValue(value: assertion.RenderedValue): ObservedValue = value match {
    case assertion.RenderedValue.Evaluated(value) => ObservedValue.Evaluated(value)
    case assertion.RenderedValue.NotEvaluated => ObservedValue.NotEvaluated
    case failure: assertion.RenderedValue.RenderingFailed => ObservedValue.RenderingFailed(failure.cause.getClass.getName, errorMessage(failure.message))
  }

  private def errorMessage(message: assertion.RenderedErrorMessage): DiagnosticErrorMessage = message match {
    case assertion.RenderedErrorMessage.Available(value) => DiagnosticErrorMessage.Available(value)
    case assertion.RenderedErrorMessage.Unavailable => DiagnosticErrorMessage.Unavailable
    case assertion.RenderedErrorMessage.AccessorFailed(cause) => DiagnosticErrorMessage.AccessorFailed(cause.getClass.getName)
  }
}
