package izumi.fundamentals.assertions

/** Per assertion invocation; emitted macro code never shares a recorder between executions. */
final class AssertionRecorder(sites: Vector[ObservationSite]) {
  private val evaluations = Array.fill[Evaluation](sites.size)(Evaluation.NotEvaluated)

  def observe[A](index: Int, value: A): A = {
    require(index >= 0 && index < evaluations.length, "Observation index outside its sites")
    evaluations(index) = Evaluation.Evaluated(CapturedValue(value))
    value
  }

  def check(result: Boolean, path: String, virtual: Boolean, span: SourceSpan, text: CompiledText, context: AssertionContext): Unit = {
    if (!result) {
      val source = ExpressionSource(SourceIdentity.recorded(path, virtual, context.sourceRoot), span, text)
      val observations = sites.indices.map(i => Observation(sites(i), evaluations(i))).toVector
      throw new AssertionFailure(AssertionDiagnostic(source, observations), context)
    }
  }
}
