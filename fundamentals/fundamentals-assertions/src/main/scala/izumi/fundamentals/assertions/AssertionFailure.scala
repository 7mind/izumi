package izumi.fundamentals.assertions

final class AssertionFailure(val diagnostic: AssertionDiagnostic, context: AssertionContext) extends AssertionError {
  lazy val rendered: RenderedAssertion = AssertionRenderer.render(diagnostic, context)

  override def getMessage: String = rendered.text

  def withClue(clue: Any): AssertionFailure = {
    val observation = Observation(ObservationSite(SourceSpan.Unavailable, CompiledText.Available("clue"), ObservationKind.Operand), Evaluation.Evaluated(CapturedValue(clue)))
    val extended = new AssertionFailure(diagnostic.copy(observations = diagnostic.observations :+ observation), context)
    extended.setStackTrace(getStackTrace)
    val cause = getCause
    if (cause != null) { val _ = extended.initCause(cause) }
    getSuppressed.foreach(extended.addSuppressed)
    extended
  }
}
