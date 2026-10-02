package izumi.fundamentals.assertions

final class AssertionFailure(val diagnostic: AssertionDiagnostic, context: AssertionContext) extends AssertionError {
  lazy val rendered: RenderedAssertion = AssertionRenderer.render(diagnostic, context)

  override def getMessage: String = rendered.text
}
